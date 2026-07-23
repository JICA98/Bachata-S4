// SPDX-License-Identifier: MIT

#include "fex_guest_engine.h"


#include "Common/Config.h"
#include "Common/HostFeatures.h"
#include <FEXCore/Config/Config.h>
#include <FEXCore/Core/Context.h>
#include <FEXCore/Core/CoreState.h>
#include <FEXCore/Core/SignalDelegator.h>
#include <FEXCore/Core/X86Enums.h>
#include <FEXCore/Debug/InternalThreadState.h>
#include <FEXCore/HLE/SyscallHandler.h>
#include <FEXCore/Utils/ArchHelpers/Arm64.h>
#include <FEXCore/Utils/LogManager.h>

#include <sys/mman.h>
#include <unistd.h>
#include <ucontext.h>

#include <array>
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <cstdio>
#include <fstream>
#include <limits>
#include <mutex>
#include <optional>
#include <shared_mutex>
#include <string>
#include <thread>
#include <tuple>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

namespace Core::Fex {
namespace {

constexpr long kRequiredPageSize = 4096;
constexpr uint32_t kFexBlockTraceLimit = 256;
constexpr uint64_t kAddLeft = 0x1122'3344'5566'7788ULL;
constexpr uint64_t kAddRight = 0x0102'0304'0506'0708ULL;
constexpr uint64_t kXorLeft = 0xfedc'ba98'7654'3210ULL;
constexpr uint64_t kXorRight = 0x0f0f'0f0f'0f0f'0f0fULL;
constexpr uint64_t kStackSentinel = 0xaabb'ccdd'eeff'0011ULL;
constexpr uint64_t kUnalignedSentinel = 0x8877'6655'4433'2211ULL;
constexpr uint64_t kThreadSentinelA = 0x1111'2222'3333'4444ULL;
constexpr uint64_t kThreadSentinelB = 0x5555'6666'7777'8888ULL;
constexpr uint64_t kCallbackInput = 0x1020'3040'5060'7080ULL;
constexpr uint64_t kInvalidationInitial = 0x0123'4567'89ab'cdefULL;
constexpr uint64_t kInvalidationUpdated = 0xfedc'ba98'7654'3210ULL;

std::atomic_uint32_t FexBlockTraceCount {};

struct ActiveFexExecutionState final {
  FEXCore::Context::Context* Context {};
  FEXCore::Core::InternalThreadState* Thread {};
  FEXCore::SignalDelegator* SignalDelegator {};
};

thread_local ActiveFexExecutionState ActiveFexExecution;

class FexExecutionSignalScope final {
public:
  FexExecutionSignalScope(FEXCore::Context::Context& context,
                          FEXCore::Core::InternalThreadState* thread,
                          FEXCore::SignalDelegator* signalDelegator)
    : Previous {ActiveFexExecution} {
    ActiveFexExecution = {&context, thread, signalDelegator};
  }

  FexExecutionSignalScope(const FexExecutionSignalScope&) = delete;
  FexExecutionSignalScope& operator=(const FexExecutionSignalScope&) = delete;

  ~FexExecutionSignalScope() {
    ActiveFexExecution = Previous;
  }

private:
  ActiveFexExecutionState Previous;
};

void FexMessageHandler(LogMan::DebugLevels level, const char* message) {
  if (message == nullptr) return;
  if (std::strstr(message, "Guest x86 Begin") != nullptr) {
    const auto index = FexBlockTraceCount.fetch_add(1, std::memory_order_relaxed);
    if (index < kFexBlockTraceLimit) {
      std::fprintf(stderr, "BACHATA_FEX_BLOCK index=%u %s\n", index, message);
    }
  } else if (level <= LogMan::ERROR) {
    std::fprintf(stderr, "BACHATA_FEX_ERROR level=%u %s\n", static_cast<unsigned>(level),
                 message);
  }
}

EngineFailure Failure(EngineStage stage, int error) {
  return {stage, error == 0 ? EIO : error};
}

bool Contains(const GuestExecutionRange& range, std::uintptr_t address, std::size_t size) {
  if (range.Begin == 0 || range.Size == 0 || size == 0 || address < range.Begin) {
    return false;
  }
  const auto rangeEnd = range.Begin + range.Size;
  const auto addressEnd = address + size;
  return rangeEnd >= range.Begin && addressEnd >= address && addressEnd <= rangeEnd;
}

bool RangesOverlap(const GuestExecutionRange& lhs, const GuestExecutionRange& rhs) {
  const auto lhsEnd = lhs.Begin + lhs.Size;
  const auto rhsEnd = rhs.Begin + rhs.Size;
  return lhsEnd >= lhs.Begin && rhsEnd >= rhs.Begin && lhs.Begin < rhsEnd && rhs.Begin < lhsEnd;
}

EngineResult<bool> ValidateHostMapping(const GuestExecutionRange& range) {
  std::ifstream maps {"/proc/self/maps"};
  if (!maps.is_open()) {
    const auto error = errno == 0 ? EACCES : errno;
    std::fprintf(stderr,
                 "BACHATA_FEX_MAPPING_FAIL reason=proc_maps_open error=%d begin=%#lx size=%#lx "
                 "executable=%d writable=%d\n",
                 error, static_cast<unsigned long>(range.Begin),
                 static_cast<unsigned long>(range.Size), range.Executable, range.Writable);
    return Failure(EngineStage::Mapping, error);
  }

  const auto rangeEnd = range.Begin + range.Size;
  std::uintptr_t covered = range.Begin;
  std::string line;
  while (std::getline(maps, line)) {
    unsigned long mapBegin {};
    unsigned long mapEnd {};
    char permissions[5] {};
    if (std::sscanf(line.c_str(), "%lx-%lx %4s", &mapBegin, &mapEnd, permissions) != 3) continue;
    const auto begin = static_cast<std::uintptr_t>(mapBegin);
    const auto end = static_cast<std::uintptr_t>(mapEnd);
    if (end <= covered) continue;
    if (begin > covered) break;
    // FEX translates guest instructions into its own executable code cache. Guest code only
    // needs to be readable on the host, and must be sealed against writes before translation.
    if (range.Executable && (permissions[0] != 'r' || permissions[1] == 'w')) {
      std::fprintf(stderr,
                   "BACHATA_FEX_MAPPING_FAIL reason=host_guest_code_permission error=%d "
                   "begin=%#lx size=%#lx executable=%d writable=%d host_begin=%#lx "
                   "host_end=%#lx host_permissions=%s\n",
                   EACCES, static_cast<unsigned long>(range.Begin),
                   static_cast<unsigned long>(range.Size), range.Executable, range.Writable,
                   static_cast<unsigned long>(begin), static_cast<unsigned long>(end), permissions);
      return Failure(EngineStage::Mapping, EACCES);
    }
    if (range.Writable && (permissions[1] != 'w' || permissions[2] == 'x')) {
      std::fprintf(stderr,
                   "BACHATA_FEX_MAPPING_FAIL reason=host_write_permission error=%d begin=%#lx "
                   "size=%#lx executable=%d writable=%d host_begin=%#lx host_end=%#lx "
                   "host_permissions=%s\n",
                   EACCES, static_cast<unsigned long>(range.Begin),
                   static_cast<unsigned long>(range.Size), range.Executable, range.Writable,
                   static_cast<unsigned long>(begin), static_cast<unsigned long>(end), permissions);
      return Failure(EngineStage::Mapping, EACCES);
    }
    covered = std::min(end, rangeEnd);
    if (covered == rangeEnd) return true;
  }
  std::fprintf(stderr,
               "BACHATA_FEX_MAPPING_FAIL reason=host_mapping_gap error=%d begin=%#lx size=%#lx "
               "executable=%d writable=%d covered=%#lx\n",
               EFAULT, static_cast<unsigned long>(range.Begin),
               static_cast<unsigned long>(range.Size), range.Executable, range.Writable,
               static_cast<unsigned long>(covered));
  return Failure(EngineStage::Mapping, EFAULT);
}

EngineResult<bool> ValidateRequest(const GuestExecutionRequest& request) {
  if (request.Rip == 0 || request.Rsp == 0 || request.MappedRanges.empty()) {
    return Failure(EngineStage::Request, EINVAL);
  }

  bool executableRip = false;
  bool writableStack = false;
  for (size_t index = 0; index < request.MappedRanges.size(); ++index) {
    const auto& range = request.MappedRanges[index];
    if (range.Begin == 0 || range.Size == 0 || range.Begin % kRequiredPageSize != 0 ||
        range.Size % kRequiredPageSize != 0 || range.Begin + range.Size < range.Begin) {
      return Failure(EngineStage::Request, EINVAL);
    }
    if (range.Executable && range.Writable) {
      std::fprintf(stderr,
                   "BACHATA_FEX_MAPPING_FAIL reason=guest_wx error=%d index=%zu begin=%#lx "
                   "size=%#lx\n",
                   EACCES, index, static_cast<unsigned long>(range.Begin),
                   static_cast<unsigned long>(range.Size));
      return Failure(EngineStage::Mapping, EACCES);
    }
    for (size_t previous = 0; previous < index; ++previous) {
      if (RangesOverlap(range, request.MappedRanges[previous])) {
        const auto& prior = request.MappedRanges[previous];
        std::fprintf(stderr,
                     "BACHATA_FEX_MAPPING_FAIL reason=guest_overlap error=%d previous=%zu "
                     "previous_begin=%#lx previous_size=%#lx index=%zu begin=%#lx size=%#lx\n",
                     EACCES, previous, static_cast<unsigned long>(prior.Begin),
                     static_cast<unsigned long>(prior.Size), index,
                     static_cast<unsigned long>(range.Begin),
                     static_cast<unsigned long>(range.Size));
        return Failure(EngineStage::Mapping, EACCES);
      }
    }
    const auto hostMapping = ValidateHostMapping(range);
    if (const auto* failure = std::get_if<EngineFailure>(&hostMapping)) return *failure;
    executableRip |= range.Executable && Contains(range, request.Rip, 1);
    writableStack |= range.Writable && Contains(range, request.Rsp, 1);
  }
  if (!executableRip || !writableStack) {
    return Failure(EngineStage::Request, EFAULT);
  }
  return true;
}

class FexConfigLease final {
public:
  static EngineResult<bool> Acquire() {
    std::scoped_lock lock {Mutex};
    if (Users == 0) {
      FEX::Config::InitializeConfigs(FEX::Config::PortableInformation {});
      FEXCore::Config::Initialize();
      FEXCore::Config::Load();
      FEXCore::Config::Set(FEXCore::Config::CONFIG_IS64BIT_MODE, "1");
      FEXCore::Config::Set(FEXCore::Config::CONFIG_DISABLETELEMETRY, "1");
      const bool traceEnabled = std::getenv("BACHATA_FEX_TRACE") != nullptr;
      FEXCore::Config::Set(FEXCore::Config::CONFIG_X86DISASSEMBLE,
                           traceEnabled ? "1" : "0");
      FexBlockTraceCount.store(0, std::memory_order_relaxed);
      LogMan::Msg::InstallHandler(FexMessageHandler);
    }
    ++Users;
    return true;
  }

  static EngineResult<bool> Release() {
    std::scoped_lock lock {Mutex};
    if (Users == 0) return Failure(EngineStage::Teardown, EINVAL);
    if (--Users == 0) {
      FEXCore::Config::Shutdown();
      LogMan::Msg::UnInstallHandler();
    }
    return true;
  }

private:
  static inline std::mutex Mutex;
  static inline size_t Users {};
};

class Mapping final {
public:
  Mapping(size_t size, int protection)
    : Size {size}
    , Address {mmap(nullptr, size, protection, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0)}
    , LastError {Address == MAP_FAILED ? errno : 0} {}

  Mapping(const Mapping&) = delete;
  Mapping& operator=(const Mapping&) = delete;

  ~Mapping() {
    if (Address != MAP_FAILED && munmap(Address, Size) != 0) {
      std::abort();
    }
  }

  [[nodiscard]] bool IsValid() const {
    return Address != MAP_FAILED;
  }

  [[nodiscard]] int Error() const {
    return LastError;
  }

  [[nodiscard]] void* Get() const {
    return Address;
  }

  [[nodiscard]] EngineResult<bool> Protect(int protection) {
    if (mprotect(Address, Size, protection) != 0) {
      return Failure(EngineStage::Mapping, errno);
    }
    return true;
  }

  [[nodiscard]] EngineResult<bool> Release() {
    if (Address == MAP_FAILED) {
      return true;
    }
    if (munmap(Address, Size) != 0) {
      return Failure(EngineStage::Teardown, errno);
    }
    Address = MAP_FAILED;
    return true;
  }

private:
  size_t Size;
  void* Address;
  int LastError;
};

class CallRetStack final {
public:
  CallRetStack()
    : Address {mmap(nullptr, kAllocationSize, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0)}
    , LastError {Address == MAP_FAILED ? errno : 0} {}

  CallRetStack(const CallRetStack&) = delete;
  CallRetStack& operator=(const CallRetStack&) = delete;

  ~CallRetStack() {
    if (Address != MAP_FAILED && munmap(Address, kAllocationSize) != 0) {
      std::abort();
    }
  }

  [[nodiscard]] bool IsReserved() const {
    return Address != MAP_FAILED;
  }

  [[nodiscard]] int Error() const {
    return LastError;
  }

  [[nodiscard]] EngineResult<bool> MakeWritable() const {
    if (mprotect(StackBase(), FEXCore::Core::InternalThreadState::CALLRET_STACK_SIZE, PROT_READ | PROT_WRITE) != 0) {
      return Failure(EngineStage::Mapping, errno);
    }
    return true;
  }

  void Initialize(FEXCore::Core::InternalThreadState* thread) const {
    thread->CallRetStackBase = StackBase();
    thread->CurrentFrame->State.callret_sp =
      reinterpret_cast<uint64_t>(StackBase()) + FEXCore::Core::InternalThreadState::CALLRET_STACK_SIZE / 4;
  }

private:
  static constexpr size_t kAllocationSize = FEXCore::Core::InternalThreadState::CALLRET_STACK_SIZE + 2 * kRequiredPageSize;

  [[nodiscard]] void* StackBase() const {
    return static_cast<uint8_t*>(Address) + kRequiredPageSize;
  }

  void* Address;
  int LastError;
};

class GuestSegmentState final {
public:
  void Initialize(FEXCore::Core::CPUState& state) {
    state.segment_arrays[FEXCore::Core::CPUState::SEGMENT_ARRAY_INDEX_GDT] = GDT.data();
    state.segment_arrays[FEXCore::Core::CPUState::SEGMENT_ARRAY_INDEX_LDT] = GDT.data();
    state.cs_idx = FEXCore::Core::CPUState::DEFAULT_USER_CS << 3;
    auto* codeSegment = FEXCore::Core::CPUState::GetSegmentFromIndex(state, state.cs_idx);
    FEXCore::Core::CPUState::SetGDTBase(codeSegment, 0);
    FEXCore::Core::CPUState::SetGDTLimit(codeSegment, 0xF'FFFFU);
    state.cs_cached = FEXCore::Core::CPUState::CalculateGDTBase(*codeSegment);
    codeSegment->L = 1;
    codeSegment->D = 0;
  }

private:
  std::array<FEXCore::Core::CPUState::gdt_segment, 32> GDT {};
};

class ThreadScope final {
public:
  ThreadScope(FEXCore::Context::Context& context, FEXCore::Core::InternalThreadState* thread)
    : Context {context}
    , Thread {thread} {}

  ThreadScope(const ThreadScope&) = delete;
  ThreadScope& operator=(const ThreadScope&) = delete;

  ~ThreadScope() {
    if (Thread != nullptr) {
      Context.DestroyThread(Thread);
    }
  }

private:
  FEXCore::Context::Context& Context;
  FEXCore::Core::InternalThreadState* Thread;
};

class BridgeSignalDelegator final : public FEXCore::SignalDelegator {
public:
  explicit BridgeSignalDelegator(uintptr_t callbackReturn)
    : CallbackReturn {callbackReturn} {}

  uintptr_t GetThunkCallbackRET() const override {
    return CallbackReturn;
  }

  void SetThunkCallbackRET(uintptr_t callbackReturn) {
    CallbackReturn = callbackReturn;
  }

private:
  uintptr_t CallbackReturn;
};

class CallbackReturnScope final {
public:
  CallbackReturnScope(BridgeSignalDelegator& delegator, uintptr_t callbackReturn)
    : Delegator {delegator}
    , Previous {delegator.GetThunkCallbackRET()} {
    Delegator.SetThunkCallbackRET(callbackReturn);
  }

  CallbackReturnScope(const CallbackReturnScope&) = delete;
  CallbackReturnScope& operator=(const CallbackReturnScope&) = delete;

  ~CallbackReturnScope() {
    Delegator.SetThunkCallbackRET(Previous);
  }

private:
  BridgeSignalDelegator& Delegator;
  uintptr_t Previous;
};

class BridgeSyscallHandler final : public FEXCore::HLE::SyscallHandler {
public:
  struct InvocationState final {
    std::optional<EngineFailure> Failure;
    uint64_t Result {};
    bool WasInvoked {};
    BridgeSyscallHandler* Owner {};
    FEXCore::Core::InternalThreadState* Thread {};

    void Reset() {
      Failure.reset();
      Result = 0;
      WasInvoked = false;
      Owner = nullptr;
      Thread = nullptr;
    }
  };

  class InvocationScope final {
  public:
    InvocationScope(BridgeSyscallHandler& owner, InvocationState& state,
                    FEXCore::Core::InternalThreadState* thread)
      : Previous {ActiveInvocation} {
      state.Reset();
      state.Owner = &owner;
      state.Thread = thread;
      ActiveInvocation = &state;
    }

    InvocationScope(const InvocationScope&) = delete;
    InvocationScope& operator=(const InvocationScope&) = delete;

    ~InvocationScope() {
      ActiveInvocation = Previous;
    }

  private:
    InvocationState* Previous;
  };

  explicit BridgeSyscallHandler(GuestBridge& bridge)
    : Bridge {bridge} {
    OSABI = FEXCore::HLE::SyscallOSABI::OS_GENERIC;
  }

  uint64_t HandleSyscall(FEXCore::Core::CpuStateFrame* frame, FEXCore::HLE::SyscallArguments*) override {
    auto* invocation = ActiveInvocation;
    if (invocation == nullptr) return static_cast<uint64_t>(-EPERM);
    if (frame == nullptr) {
      invocation->Failure = Failure(EngineStage::Bridge, EFAULT);
      return static_cast<uint64_t>(-EFAULT);
    }

    GuestCpu::HleCallFrame hleFrame{};
    hleFrame.operation = frame->State.gregs[FEXCore::X86State::REG_RAX];
    std::copy(std::begin(frame->State.gregs), std::end(frame->State.gregs), hleFrame.gpr.begin());
    hleFrame.gpr[FEXCore::X86State::REG_RCX] = frame->State.gregs[FEXCore::X86State::REG_R10];
    hleFrame.rsp = frame->State.gregs[FEXCore::X86State::REG_RSP];
    for (size_t index = 0; index < hleFrame.xmm.size(); ++index) {
      hleFrame.xmm[index] = {frame->State.xmm.sse.data[index][0], frame->State.xmm.sse.data[index][1]};
    }
    auto result = Bridge.Invoke(hleFrame);
    if (const auto* error = std::get_if<EngineFailure>(&result)) {
      invocation->Failure = *error;
      frame->State.gregs[FEXCore::X86State::REG_RAX] = static_cast<uint64_t>(-error->Error);
      return frame->State.gregs[FEXCore::X86State::REG_RAX];
    }

    std::copy(hleFrame.gpr.begin(), hleFrame.gpr.end(), std::begin(frame->State.gregs));
    for (size_t index = 0; index < hleFrame.xmm.size(); ++index) {
      frame->State.xmm.sse.data[index][0] = hleFrame.xmm[index][0];
      frame->State.xmm.sse.data[index][1] = hleFrame.xmm[index][1];
    }
    invocation->Result = frame->State.gregs[FEXCore::X86State::REG_RAX];
    invocation->WasInvoked = true;
    return invocation->Result;
  }

  EngineResult<bool> RegisterThread(FEXCore::Core::InternalThreadState* thread,
                                    const std::vector<GuestExecutionRange>& ranges) {
    if (thread == nullptr) return Failure(EngineStage::Thread, EINVAL);
    std::scoped_lock lock {RangesMutex};
    const auto [_, inserted] = ExecutableRanges.emplace(thread, ranges);
    return inserted ? EngineResult<bool> {true} : EngineResult<bool> {Failure(EngineStage::Thread, EEXIST)};
  }

  void UnregisterThread(FEXCore::Core::InternalThreadState* thread) {
    std::scoped_lock lock {RangesMutex};
    ExecutableRanges.erase(thread);
  }

  FEXCore::HLE::ExecutableRangeInfo QueryGuestExecutableRange(FEXCore::Core::InternalThreadState* thread,
                                                               uint64_t address) override {
    {
      std::scoped_lock lock {RangesMutex};
      const auto ranges = ExecutableRanges.find(thread);
      if (ranges != ExecutableRanges.end()) {
        for (const auto& range : ranges->second) {
          if (range.Executable && Contains(range, static_cast<std::uintptr_t>(address), 1)) {
            return {range.Begin, range.Size, range.Writable};
          }
        }
      }
    }
    const auto dynamicRange = Bridge.QueryExecutableRange(static_cast<std::uintptr_t>(address));
    if (dynamicRange && dynamicRange->Executable && Contains(*dynamicRange, address, 1)) {
      return {dynamicRange->Begin, dynamicRange->Size, dynamicRange->Writable};
    }
    return {};
  }

  FEXCore::Core::InternalThreadState* ActiveThread() const {
    if (ActiveInvocation == nullptr || ActiveInvocation->Owner != this) return nullptr;
    return ActiveInvocation->Thread;
  }

  const std::optional<EngineFailure>& ActiveFailure() const {
    static const std::optional<EngineFailure> noFailure;
    if (ActiveInvocation == nullptr || ActiveInvocation->Owner != this) return noFailure;
    return ActiveInvocation->Failure;
  }

  bool IsWritableRange(FEXCore::Core::InternalThreadState* thread, std::uintptr_t address,
                       std::size_t size) {
    std::scoped_lock lock {RangesMutex};
    const auto ranges = ExecutableRanges.find(thread);
    if (ranges == ExecutableRanges.end()) return false;
    return std::ranges::any_of(ranges->second, [&](const GuestExecutionRange& range) {
      return range.Writable && Contains(range, address, size);
    });
  }

  std::optional<FEXCore::ExecutableFileSectionInfo>
  LookupExecutableFileSection(FEXCore::Core::InternalThreadState*, uint64_t) override {
    return std::nullopt;
  }

  [[nodiscard]] const std::optional<EngineFailure>& FailureResult(const InvocationState& state) const {
    return state.Failure;
  }

  [[nodiscard]] uint64_t Result(const InvocationState& state) const {
    return state.Result;
  }

  [[nodiscard]] bool Invoked(const InvocationState& state) const {
    return state.WasInvoked;
  }

private:
  static thread_local InvocationState* ActiveInvocation;

  GuestBridge& Bridge;
  std::mutex RangesMutex;
  std::unordered_map<FEXCore::Core::InternalThreadState*, std::vector<GuestExecutionRange>> ExecutableRanges;
};

thread_local BridgeSyscallHandler::InvocationState* BridgeSyscallHandler::ActiveInvocation {};

class ExecutableRangeScope final {
public:
  ExecutableRangeScope(BridgeSyscallHandler& handler, FEXCore::Core::InternalThreadState* thread)
    : Handler {handler}
    , Thread {thread} {}

  ExecutableRangeScope(const ExecutableRangeScope&) = delete;
  ExecutableRangeScope& operator=(const ExecutableRangeScope&) = delete;

  ~ExecutableRangeScope() {
    Handler.UnregisterThread(Thread);
  }

private:
  BridgeSyscallHandler& Handler;
  FEXCore::Core::InternalThreadState* Thread;
};

void AppendImmediate(std::vector<uint8_t>& code, uint64_t value) {
  const auto offset = code.size();
  code.resize(offset + sizeof(value));
  std::memcpy(code.data() + offset, &value, sizeof(value));
}

uint64_t DoubleBits(double value) {
  uint64_t bits {};
  std::memcpy(&bits, &value, sizeof(bits));
  return bits;
}

struct GuestCode final {
  std::vector<uint8_t> Bytes;
  size_t CallbackOffset {};
  size_t CallbackReturnOffset {};
  size_t InvalidationOffset {};
  size_t InvalidationImmediateOffset {};
  size_t ThreadOffset {};
};

GuestCode BuildGuestCode() {
  constexpr uint64_t bridgeOperation = 0xB4C4'F001ULL;
  constexpr uint64_t bridgeArgument = 0x1020'3040'5060'7080ULL;

  GuestCode result;
  auto& code = result.Bytes;
  code.insert(code.end(), {0x48, 0xb8}); // mov rax, kAddLeft
  AppendImmediate(code, kAddLeft);
  code.insert(code.end(), {0x48, 0xbb}); // mov rbx, kAddRight
  AppendImmediate(code, kAddRight);
  code.insert(code.end(), {0x48, 0x01, 0xd8}); // add rax, rbx
  code.insert(code.end(), {0x49, 0x89, 0xc0}); // mov r8, rax
  code.insert(code.end(), {0x48, 0xb9}); // mov rcx, kXorLeft
  AppendImmediate(code, kXorLeft);
  code.insert(code.end(), {0x48, 0xba}); // mov rdx, kXorRight
  AppendImmediate(code, kXorRight);
  code.insert(code.end(), {0x48, 0x31, 0xd1}); // xor rcx, rdx
  code.insert(code.end(), {0x49, 0x89, 0xc9}); // mov r9, rcx
  code.insert(code.end(), {0xf2, 0x0f, 0x58, 0xc1}); // addsd xmm0, xmm1

  code.insert(code.end(), {0x48, 0xb8}); // mov rax, bridge operation
  AppendImmediate(code, bridgeOperation);
  code.insert(code.end(), {0x48, 0xbf}); // mov rdi, bridge argument
  AppendImmediate(code, bridgeArgument);
  code.insert(code.end(), {0x0f, 0x05}); // syscall
  code.insert(code.end(), {0x4d, 0x8b, 0x2c, 0x24}); // mov r13, [r12]

  code.push_back(0xe8); // call stack_test
  const size_t callDisplacementOffset = code.size();
  code.resize(code.size() + sizeof(int32_t));
  code.push_back(0xf4); // hlt

  const size_t stackTestOffset = code.size();
  code.insert(code.end(), {0x48, 0xba}); // mov rdx, kStackSentinel
  AppendImmediate(code, kStackSentinel);
  code.push_back(0x52); // push rdx
  code.insert(code.end(), {0x48, 0x8b, 0x34, 0x24}); // mov rsi, [rsp]
  code.push_back(0x5f); // pop rdi
  code.push_back(0xc3); // ret
  const auto displacement = static_cast<int32_t>(stackTestOffset - (callDisplacementOffset + sizeof(int32_t)));
  std::memcpy(code.data() + callDisplacementOffset, &displacement, sizeof(displacement));

  result.CallbackOffset = code.size();
  code.insert(code.end(), {0x48, 0x8d, 0x47, 0x05, 0xc3}); // lea rax, [rdi + 5]; ret
  result.CallbackReturnOffset = code.size();
  code.insert(code.end(), {0x0f, 0x3e}); // FEX CALLBACKRET instruction

  result.InvalidationOffset = code.size();
  code.insert(code.end(), {0x48, 0xb8}); // mov rax, kInvalidationInitial
  result.InvalidationImmediateOffset = code.size();
  AppendImmediate(code, kInvalidationInitial);
  code.push_back(0xf4); // hlt

  result.ThreadOffset = code.size();
  code.insert(code.end(), {0x64, 0x48, 0x8b, 0x04, 0x25, 0x00, 0x00, 0x00, 0x00}); // mov rax, fs:[0]
  code.push_back(0xf4); // hlt
  return result;
}

} // namespace

bool HandleGuestSignal(int signal, siginfo_t* info, void* rawContext) noexcept {
#if defined(__aarch64__)
  if (signal != SIGBUS || info == nullptr || rawContext == nullptr ||
      ActiveFexExecution.Context == nullptr || ActiveFexExecution.Thread == nullptr ||
      ActiveFexExecution.SignalDelegator == nullptr) {
    return false;
  }

  auto* context = reinterpret_cast<ucontext_t*>(rawContext);
  const auto pc = static_cast<std::uintptr_t>(context->uc_mcontext.pc);
  if (!ActiveFexExecution.Context->IsAddressInCodeBuffer(ActiveFexExecution.Thread, pc)) {
    return false;
  }

  auto* registers = reinterpret_cast<std::uint64_t*>(context->uc_mcontext.regs);
  if (info->si_code != BUS_ADRALN) return false;
  const auto adjustment = FEXCore::ArchHelpers::Arm64::HandleUnalignedAccess(
      ActiveFexExecution.Thread,
      FEXCore::ArchHelpers::Arm64::UnalignedHandlerType::HalfBarrier, pc, registers);
  if (!adjustment.has_value()) {
    return false;
  }
  context->uc_mcontext.pc = pc + *adjustment;
  return true;
#else
  static_cast<void>(signal);
  static_cast<void>(info);
  static_cast<void>(rawContext);
  return false;
#endif
}

class GuestEngine::Thread final {
public:
  Thread(std::thread::id owner, GuestExecutionRequest request)
    : Owner {owner}
    , Request {std::move(request)} {}

  std::thread::id Owner;
  GuestExecutionRequest Request;
  FEXCore::Core::InternalThreadState* Native {};
  CallRetStack CallRet;
  GuestSegmentState Segments;
  BridgeSyscallHandler::InvocationState Invocation;
  uint64_t FirstRip {};
  uint64_t LastRip {};
};

class GuestEngine::Impl final {
public:
  explicit Impl(GuestBridge& bridge)
    : Bridge {bridge} {}

  [[nodiscard]] EngineResult<GuestRunResult> Run() {
    if (Context == nullptr || SignalDelegator == nullptr || Syscalls == nullptr) {
      return Failure(EngineStage::Context, EINVAL);
    }
    if (Ran) {
      return Failure(EngineStage::Execute, EALREADY);
    }

    GuestCode guest = BuildGuestCode();
    if (guest.Bytes.size() > PageSize) return Failure(EngineStage::Mapping, E2BIG);
    Mapping code {PageSize, PROT_READ | PROT_WRITE};
    if (!code.IsValid()) return Failure(EngineStage::Mapping, code.Error());
    std::memcpy(code.Get(), guest.Bytes.data(), guest.Bytes.size());
    __builtin___clear_cache(reinterpret_cast<char*>(code.Get()), reinterpret_cast<char*>(code.Get()) + PageSize);
    const auto codeProtection = code.Protect(PROT_READ);
    if (const auto* error = std::get_if<EngineFailure>(&codeProtection)) return *error;

    Mapping stackPage {PageSize, PROT_READ | PROT_WRITE};
    Mapping tlsPage {PageSize, PROT_READ | PROT_WRITE};
    if (!stackPage.IsValid()) return Failure(EngineStage::Mapping, stackPage.Error());
    if (!tlsPage.IsValid()) return Failure(EngineStage::Mapping, tlsPage.Error());
    std::memcpy(tlsPage.Get(), &kThreadSentinelA, sizeof(kThreadSentinelA));

    const uint64_t initialRip = reinterpret_cast<uint64_t>(code.Get());
    CallbackReturnScope callbackReturn {*SignalDelegator, initialRip + guest.CallbackReturnOffset};
    const uint64_t initialRsp = reinterpret_cast<uint64_t>(stackPage.Get()) + PageSize - 16;
    CallRetStack callRetStack;
    if (!callRetStack.IsReserved()) return Failure(EngineStage::Mapping, callRetStack.Error());
    const auto callRetStackProtection = callRetStack.MakeWritable();
    if (const auto* error = std::get_if<EngineFailure>(&callRetStackProtection)) return *error;

    auto* thread = Context->CreateThread(initialRip, initialRsp);
    if (thread == nullptr) return Failure(EngineStage::Thread, ENOMEM);
    ThreadScope threadScope {*Context, thread};
    const std::vector<GuestExecutionRange> ranges {
        {static_cast<std::uintptr_t>(initialRip), PageSize, true, false},
    };
    const auto registered = Syscalls->RegisterThread(thread, ranges);
    if (const auto* failure = std::get_if<EngineFailure>(&registered)) return *failure;
    ExecutableRangeScope rangeScope {*Syscalls, thread};
    BridgeSyscallHandler::InvocationState invocation;
    BridgeSyscallHandler::InvocationScope invocationScope {*Syscalls, invocation, thread};
    GuestSegmentState segmentState;
    segmentState.Initialize(thread->CurrentFrame->State);
    callRetStack.Initialize(thread);
    thread->CurrentFrame->State.fs_cached = reinterpret_cast<uint64_t>(tlsPage.Get());

    std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> initialXmm {};
    std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> initialYmmHigh {};
    constexpr double fpLeft = 1.5;
    constexpr double fpRight = 2.25;
    const auto fpLeftBits = DoubleBits(fpLeft);
    const auto fpRightBits = DoubleBits(fpRight);
    std::memcpy(&initialXmm[0], &fpLeftBits, sizeof(fpLeftBits));
    std::memcpy(&initialXmm[1], &fpRightBits, sizeof(fpRightBits));
    Context->SetXMMRegistersFromState(thread, initialXmm.data(), HostFeatures.SupportsAVX ? initialYmmHigh.data() : nullptr);

    std::memcpy(static_cast<uint8_t*>(stackPage.Get()) + 1, &kUnalignedSentinel,
                sizeof(kUnalignedSentinel));
    thread->CurrentFrame->State.gregs[FEXCore::X86State::REG_R12] =
        reinterpret_cast<uint64_t>(stackPage.Get()) + 1;
    {
      FexExecutionSignalScope signalScope {*Context, thread, SignalDelegator.get()};
      Context->ExecuteThread(thread);
    }
    if (Syscalls->FailureResult(invocation)) return *Syscalls->FailureResult(invocation);

    auto& state = thread->CurrentFrame->State;
    GuestRunResult result;
    result.Gpr = state.gregs[FEXCore::X86State::REG_R8] == kAddLeft + kAddRight &&
                 state.gregs[FEXCore::X86State::REG_R9] == (kXorLeft ^ kXorRight) &&
                 state.gregs[FEXCore::X86State::REG_RSI] == kStackSentinel &&
                 state.gregs[FEXCore::X86State::REG_RDI] == kStackSentinel &&
                 state.gregs[FEXCore::X86State::REG_RSP] == initialRsp;
    const auto rflags = Context->ReconstructCompactedEFLAGS(thread, false, nullptr, 0);
    result.Rflags = (rflags & ((1U << 0) | (1U << 1) | (1U << 6) | (1U << 11))) == (1U << 1);
    result.Bridge = Syscalls->Invoked(invocation) && state.gregs[FEXCore::X86State::REG_RAX] == Syscalls->Result(invocation);
    result.Unaligned =
        state.gregs[FEXCore::X86State::REG_R13] == kUnalignedSentinel;

    std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> finalXmm {};
    std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> finalYmmHigh {};
    Context->ReconstructXMMRegisters(thread, finalXmm.data(), HostFeatures.SupportsAVX ? finalYmmHigh.data() : nullptr);
    uint64_t fpResultBits {};
    std::memcpy(&fpResultBits, &finalXmm[0], sizeof(fpResultBits));
    result.Xmm = fpResultBits == DoubleBits(fpLeft + fpRight);
    if (!result.Gpr || !result.Rflags || !result.Xmm || !result.Bridge) {
      return Failure(EngineStage::Execute, EPROTO);
    }

    bool firstThread = false;
    bool secondThread = false;
    std::thread firstHostThread {[&] { firstThread = ExecuteThreadTlsCheck(initialRip + guest.ThreadOffset, kThreadSentinelA); }};
    std::thread secondHostThread {[&] { secondThread = ExecuteThreadTlsCheck(initialRip + guest.ThreadOffset, kThreadSentinelB); }};
    firstHostThread.join();
    secondHostThread.join();
    result.Threads = firstThread && secondThread;
    result.Tls = result.Threads;
    if (!result.Threads) return Failure(EngineStage::Thread, EPROTO);

    state.gregs[FEXCore::X86State::REG_RDI] = kCallbackInput;
    state.gregs[FEXCore::X86State::REG_RSP] = initialRsp;
    {
      FexExecutionSignalScope signalScope {*Context, thread, SignalDelegator.get()};
      Context->HandleCallback(thread, initialRip + guest.CallbackOffset);
    }
    if (state.gregs[FEXCore::X86State::REG_RAX] != kCallbackInput + 5 || state.gregs[FEXCore::X86State::REG_RSP] != initialRsp) {
      return Failure(EngineStage::Execute, EPROTO);
    }

    const uint64_t invalidationRip = initialRip + guest.InvalidationOffset;
    state.rip = invalidationRip;
    state.gregs[FEXCore::X86State::REG_RSP] = initialRsp;
    {
      FexExecutionSignalScope signalScope {*Context, thread, SignalDelegator.get()};
      Context->ExecuteThread(thread);
    }
    if (state.gregs[FEXCore::X86State::REG_RAX] != kInvalidationInitial) return Failure(EngineStage::Invalidate, EPROTO);

    auto* invalidationImmediate = static_cast<uint8_t*>(code.Get()) + guest.InvalidationImmediateOffset;
    {
      std::scoped_lock codeInvalidationLock {Context->GetCodeInvalidationMutex()};
      const auto writable = code.Protect(PROT_READ | PROT_WRITE);
      if (const auto* error = std::get_if<EngineFailure>(&writable)) return *error;
      std::memcpy(invalidationImmediate, &kInvalidationUpdated, sizeof(kInvalidationUpdated));
      __builtin___clear_cache(reinterpret_cast<char*>(code.Get()), reinterpret_cast<char*>(code.Get()) + PageSize);
      const auto executable = code.Protect(PROT_READ);
      if (const auto* error = std::get_if<EngineFailure>(&executable)) return *error;
      Context->InvalidateCodeBuffersCodeRange(invalidationRip, 11);
      Context->InvalidateThreadCachedCodeRange(thread, invalidationRip, 11);
    }
    state.rip = invalidationRip;
    state.gregs[FEXCore::X86State::REG_RSP] = initialRsp;
    {
      FexExecutionSignalScope signalScope {*Context, thread, SignalDelegator.get()};
      Context->ExecuteThread(thread);
    }
    result.Invalidation = state.gregs[FEXCore::X86State::REG_RAX] == kInvalidationUpdated;
    if (!result.Invalidation) return Failure(EngineStage::Invalidate, EPROTO);

    Ran = true;
    return result;
  }

  [[nodiscard]] EngineResult<bool> Shutdown() {
    {
      std::scoped_lock lock {ThreadsMutex};
      if (!Threads.empty()) {
        return Failure(EngineStage::Teardown, EBUSY);
      }
    }
    Context.reset();
    SignalDelegator.reset();
    Syscalls.reset();
    CallbackReturn.reset();
    FunctionReturn.reset();
    if (ConfigInitialized) {
      const auto configRelease = FexConfigLease::Release();
      if (const auto* failure = std::get_if<EngineFailure>(&configRelease)) return *failure;
      ConfigInitialized = false;
    }
    return true;
  }

  [[nodiscard]] bool ExecuteThreadTlsCheck(uint64_t rip, uint64_t sentinel) {
    Mapping stackPage {PageSize, PROT_READ | PROT_WRITE};
    Mapping tlsPage {PageSize, PROT_READ | PROT_WRITE};
    if (!stackPage.IsValid() || !tlsPage.IsValid()) return false;
    std::memcpy(tlsPage.Get(), &sentinel, sizeof(sentinel));
    CallRetStack callRetStack;
    if (!callRetStack.IsReserved()) return false;
    const auto callRetStackProtection = callRetStack.MakeWritable();
    if (std::holds_alternative<EngineFailure>(callRetStackProtection)) return false;
    const auto initialRsp = reinterpret_cast<uint64_t>(stackPage.Get()) + PageSize - 16;
    auto* thread = Context->CreateThread(rip, initialRsp);
    if (thread == nullptr) return false;
    ThreadScope threadScope {*Context, thread};
    const std::vector<GuestExecutionRange> ranges {
        {static_cast<std::uintptr_t>(rip & ~(PageSize - 1)), PageSize, true, false},
    };
    const auto registered = Syscalls->RegisterThread(thread, ranges);
    if (std::holds_alternative<EngineFailure>(registered)) return false;
    ExecutableRangeScope rangeScope {*Syscalls, thread};
    BridgeSyscallHandler::InvocationState invocation;
    BridgeSyscallHandler::InvocationScope invocationScope {*Syscalls, invocation, thread};
    GuestSegmentState segmentState;
    segmentState.Initialize(thread->CurrentFrame->State);
    callRetStack.Initialize(thread);
    thread->CurrentFrame->State.fs_cached = reinterpret_cast<uint64_t>(tlsPage.Get());
    {
      FexExecutionSignalScope signalScope {*Context, thread, SignalDelegator.get()};
      Context->ExecuteThread(thread);
    }
    return thread->CurrentFrame->State.gregs[FEXCore::X86State::REG_RAX] == sentinel &&
           thread->CurrentFrame->State.gregs[FEXCore::X86State::REG_RSP] == initialRsp;
  }

  GuestBridge& Bridge;
  FEXCore::HostFeatures HostFeatures {};
  fextl::unique_ptr<FEXCore::Context::Context> Context;
  std::unique_ptr<BridgeSignalDelegator> SignalDelegator;
  std::unique_ptr<BridgeSyscallHandler> Syscalls;
  std::unique_ptr<Mapping> CallbackReturn;
  std::unique_ptr<Mapping> FunctionReturn;
  size_t PageSize {};
  bool ConfigInitialized {};
  bool Ran {};
  std::mutex ThreadsMutex;
  std::unordered_set<Thread*> Threads;
};

GuestEngine::GuestEngine(std::unique_ptr<Impl> impl)
  : ImplState {std::move(impl)} {}

GuestEngine::~GuestEngine() {
  if (ImplState != nullptr && std::holds_alternative<EngineFailure>(Shutdown())) {
    std::abort();
  }
}

EngineResult<std::unique_ptr<GuestEngine>> GuestEngine::Create(GuestBridge& bridge) {
  const auto pageSize = sysconf(_SC_PAGESIZE);
  if (pageSize != kRequiredPageSize) return Failure(EngineStage::Mapping, ENOTSUP);

  auto impl = std::make_unique<Impl>(bridge);
  impl->PageSize = static_cast<size_t>(pageSize);
  const auto configAcquire = FexConfigLease::Acquire();
  if (const auto* failure = std::get_if<EngineFailure>(&configAcquire)) return *failure;
  impl->ConfigInitialized = true;
  const auto fail = [&impl](EngineFailure original) -> EngineResult<std::unique_ptr<GuestEngine>> {
    const auto teardown = impl->Shutdown();
    if (const auto* error = std::get_if<EngineFailure>(&teardown)) return *error;
    return original;
  };

  impl->HostFeatures = FEX::FetchHostFeatures();
  impl->Context = FEXCore::Context::Context::CreateNewContext(impl->HostFeatures);
  if (impl->Context == nullptr) {
    return fail(Failure(EngineStage::Context, ENOMEM));
  }
  impl->FunctionReturn =
      std::make_unique<Mapping>(impl->PageSize, PROT_READ | PROT_WRITE);
  if (!impl->FunctionReturn->IsValid()) {
    return fail(Failure(EngineStage::Mapping, impl->FunctionReturn->Error()));
  }
  *static_cast<uint8_t*>(impl->FunctionReturn->Get()) = 0xf4;
  __builtin___clear_cache(static_cast<char*>(impl->FunctionReturn->Get()),
                          static_cast<char*>(impl->FunctionReturn->Get()) + impl->PageSize);
  const auto functionProtection = impl->FunctionReturn->Protect(PROT_READ | PROT_EXEC);
  if (const auto* failure = std::get_if<EngineFailure>(&functionProtection)) {
    return fail(*failure);
  }

  impl->CallbackReturn =
      std::make_unique<Mapping>(impl->PageSize, PROT_READ | PROT_WRITE);
  if (!impl->CallbackReturn->IsValid()) {
    return fail(Failure(EngineStage::Mapping, impl->CallbackReturn->Error()));
  }
  auto* callbackReturn = static_cast<uint8_t*>(impl->CallbackReturn->Get());
  callbackReturn[0] = 0x0f;
  callbackReturn[1] = 0x3e;
  __builtin___clear_cache(static_cast<char*>(impl->CallbackReturn->Get()),
                          static_cast<char*>(impl->CallbackReturn->Get()) + impl->PageSize);
  const auto callbackProtection = impl->CallbackReturn->Protect(PROT_READ | PROT_EXEC);
  if (const auto* failure = std::get_if<EngineFailure>(&callbackProtection)) {
    return fail(*failure);
  }
  impl->SignalDelegator = std::make_unique<BridgeSignalDelegator>(
      reinterpret_cast<std::uintptr_t>(impl->CallbackReturn->Get()));
  impl->Syscalls = std::make_unique<BridgeSyscallHandler>(impl->Bridge);
  impl->Context->SetSignalDelegator(impl->SignalDelegator.get());
  impl->Context->SetSyscallHandler(impl->Syscalls.get());
  impl->Context->EnableExitOnHLT();
  if (!impl->Context->InitCore()) {
    return fail(Failure(EngineStage::Context, EIO));
  }

  return std::unique_ptr<GuestEngine> {new GuestEngine {std::move(impl)}};
}

EngineResult<GuestRunResult> GuestEngine::RunControlledHarness() {
  if (ImplState == nullptr) return Failure(EngineStage::Teardown, ESHUTDOWN);
  return ImplState->Run();
}

EngineResult<GuestEngine::Thread*> GuestEngine::CreateThread(const GuestExecutionRequest& request) {
  if (ImplState == nullptr || ImplState->Context == nullptr || ImplState->Syscalls == nullptr) {
    return Failure(EngineStage::Teardown, ESHUTDOWN);
  }
  const auto validation = ValidateRequest(request);
  if (const auto* failure = std::get_if<EngineFailure>(&validation)) {
    return *failure;
  }

  auto thread = std::make_unique<Thread>(std::this_thread::get_id(), request);
  if (!thread->CallRet.IsReserved()) {
    return Failure(EngineStage::Mapping, thread->CallRet.Error());
  }
  const auto callRetWritable = thread->CallRet.MakeWritable();
  if (const auto* failure = std::get_if<EngineFailure>(&callRetWritable)) {
    return *failure;
  }

  thread->Native = ImplState->Context->CreateThread(request.Rip, request.Rsp);
  if (thread->Native == nullptr) {
    return Failure(EngineStage::Thread, ENOMEM);
  }

  auto& state = thread->Native->CurrentFrame->State;
  thread->Segments.Initialize(state);
  state.fs_cached = request.FsBase;
  state.gs_cached = request.GsBase;
  thread->CallRet.Initialize(thread->Native);
  state.rip = request.Rip;
  std::copy(request.Gpr.begin(), request.Gpr.end(), std::begin(state.gregs));
  state.gregs[FEXCore::X86State::REG_RSP] = request.Rsp;
  ImplState->Context->SetFlagsFromCompactedEFLAGS(thread->Native, static_cast<uint32_t>(request.Rflags));

  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> xmm {};
  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> ymmHigh {};
  static_assert(std::tuple_size_v<decltype(request.Xmm)> == FEXCore::Core::CPUState::NUM_XMMS);
  for (size_t index = 0; index < xmm.size(); ++index) {
    std::memcpy(&xmm[index], request.Xmm[index].data(), sizeof(xmm[index]));
  }
  ImplState->Context->SetXMMRegistersFromState(thread->Native, xmm.data(),
                                               ImplState->HostFeatures.SupportsAVX ? ymmHigh.data() : nullptr);
  thread->FirstRip = request.Rip;
  const auto registration = ImplState->Syscalls->RegisterThread(thread->Native, request.MappedRanges);
  if (const auto* failure = std::get_if<EngineFailure>(&registration)) {
    ImplState->Context->DestroyThread(thread->Native);
    thread->Native = nullptr;
    return *failure;
  }
  {
    std::scoped_lock lock {ImplState->ThreadsMutex};
    const auto [_, inserted] = ImplState->Threads.insert(thread.get());
    if (!inserted) {
      ImplState->Syscalls->UnregisterThread(thread->Native);
      ImplState->Context->DestroyThread(thread->Native);
      thread->Native = nullptr;
      return Failure(EngineStage::Thread, EEXIST);
    }
  }
  return thread.release();
}

EngineResult<GuestExecutionState> GuestEngine::Run(Thread& thread) {
  if (ImplState == nullptr || ImplState->Context == nullptr || thread.Native == nullptr) {
    return Failure(EngineStage::Teardown, ESHUTDOWN);
  }
  {
    std::scoped_lock lock {ImplState->ThreadsMutex};
    if (!ImplState->Threads.contains(&thread) || thread.Owner != std::this_thread::get_id()) {
      return Failure(EngineStage::Thread, EPERM);
    }
  }

  BridgeSyscallHandler::InvocationScope invocationScope {*ImplState->Syscalls, thread.Invocation,
                                                          thread.Native};
  {
    FexExecutionSignalScope signalScope {*ImplState->Context, thread.Native,
                                         ImplState->SignalDelegator.get()};
    ImplState->Context->ExecuteThread(thread.Native);
  }
  if (ImplState->Syscalls->FailureResult(thread.Invocation)) {
    return *ImplState->Syscalls->FailureResult(thread.Invocation);
  }

  const auto& frame = thread.Native->CurrentFrame->State;
  GuestExecutionState result;
  result.FirstRip = thread.FirstRip;
  result.Rip = frame.rip;
  result.Rsp = frame.gregs[FEXCore::X86State::REG_RSP];
  std::copy(std::begin(frame.gregs), std::end(frame.gregs), result.Gpr.begin());
  result.Rflags = ImplState->Context->ReconstructCompactedEFLAGS(thread.Native, false, nullptr, 0);
  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> xmm {};
  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> ymmHigh {};
  ImplState->Context->ReconstructXMMRegisters(thread.Native, xmm.data(),
                                               ImplState->HostFeatures.SupportsAVX ? ymmHigh.data() : nullptr);
  for (size_t index = 0; index < xmm.size(); ++index) {
    std::memcpy(result.Xmm[index].data(), &xmm[index], sizeof(xmm[index]));
  }
  thread.LastRip = result.Rip;
  result.LastRip = thread.LastRip;
  // This Phase-1 context exits only because EnableExitOnHLT() is active.
  result.StopReason = GuestStopReason::Halted;
  return result;
}

EngineResult<GuestExecutionState> GuestEngine::CallGuest(
    std::uintptr_t rip, std::span<const std::uint64_t> arguments) {
  if (ImplState == nullptr || ImplState->Context == nullptr || ImplState->Syscalls == nullptr) {
    return Failure(EngineStage::Teardown, ESHUTDOWN);
  }
  auto* thread = ImplState->Syscalls->ActiveThread();
  if (thread == nullptr) return Failure(EngineStage::Thread, ENXIO);
  const auto executable = ImplState->Syscalls->QueryGuestExecutableRange(thread, rip);
  if (executable.Size == 0) return Failure(EngineStage::Mapping, EFAULT);
  if (arguments.size() > 7) return Failure(EngineStage::Request, E2BIG);

  auto& frame = thread->CurrentFrame->State;
  constexpr std::array<size_t, 6> argumentRegisters {
      FEXCore::X86State::REG_RDI, FEXCore::X86State::REG_RSI, FEXCore::X86State::REG_RDX,
      FEXCore::X86State::REG_RCX, FEXCore::X86State::REG_R8, FEXCore::X86State::REG_R9,
  };
  const auto registerCount = std::min(arguments.size(), argumentRegisters.size());
  for (size_t index = 0; index < registerCount; ++index) {
    frame.gregs[argumentRegisters[index]] = arguments[index];
  }
  if (arguments.size() == 7) {
    const auto rsp = frame.gregs[FEXCore::X86State::REG_RSP];
    if (rsp < sizeof(uint64_t) ||
        !ImplState->Syscalls->IsWritableRange(thread, rsp - sizeof(uint64_t),
                                               sizeof(uint64_t))) {
      return Failure(EngineStage::Mapping, EFAULT);
    }
    std::memcpy(reinterpret_cast<void*>(rsp - sizeof(uint64_t)), &arguments[6],
                sizeof(uint64_t));
  }

  BridgeSyscallHandler::InvocationState invocation;
  BridgeSyscallHandler::InvocationScope invocationScope {*ImplState->Syscalls, invocation, thread};
  {
    FexExecutionSignalScope signalScope {*ImplState->Context, thread,
                                         ImplState->SignalDelegator.get()};
    ImplState->Context->HandleCallback(thread, rip);
  }
  if (ImplState->Syscalls->FailureResult(invocation)) {
    return *ImplState->Syscalls->FailureResult(invocation);
  }
  if (ImplState->Syscalls->ActiveThread() != thread) {
    return Failure(EngineStage::Thread, EFAULT);
  }

  GuestExecutionState result;
  result.FirstRip = rip;
  result.Rip = frame.rip;
  result.LastRip = frame.rip;
  result.Rsp = frame.gregs[FEXCore::X86State::REG_RSP];
  std::copy(std::begin(frame.gregs), std::end(frame.gregs), result.Gpr.begin());
  result.Rflags = ImplState->Context->ReconstructCompactedEFLAGS(thread, false, nullptr, 0);
  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> xmm {};
  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> ymmHigh {};
  ImplState->Context->ReconstructXMMRegisters(
      thread, xmm.data(), ImplState->HostFeatures.SupportsAVX ? ymmHigh.data() : nullptr);
  for (size_t index = 0; index < xmm.size(); ++index) {
    std::memcpy(result.Xmm[index].data(), &xmm[index], sizeof(xmm[index]));
  }
  result.StopReason = GuestStopReason::Returned;
  return result;
}

EngineResult<bool> GuestEngine::Invalidate(Thread& thread, std::uintptr_t begin, std::size_t size) {
  if (ImplState == nullptr || ImplState->Context == nullptr || thread.Native == nullptr) {
    return Failure(EngineStage::Teardown, ESHUTDOWN);
  }
  {
    std::scoped_lock lock {ImplState->ThreadsMutex};
    if (!ImplState->Threads.contains(&thread) || thread.Owner != std::this_thread::get_id()) {
      return Failure(EngineStage::Thread, EPERM);
    }
  }
  if (begin == 0 || size == 0 || begin % kRequiredPageSize != 0 || size % kRequiredPageSize != 0) {
    return Failure(EngineStage::Request, EINVAL);
  }
  const auto executable = std::any_of(thread.Request.MappedRanges.begin(), thread.Request.MappedRanges.end(),
                                      [begin, size](const GuestExecutionRange& range) {
                                        return range.Executable && !range.Writable && Contains(range, begin, size);
                                      });
  if (!executable) {
    return Failure(EngineStage::Invalidate, EFAULT);
  }

  std::scoped_lock lock {ImplState->Context->GetCodeInvalidationMutex()};
  ImplState->Context->InvalidateCodeBuffersCodeRange(begin, size);
  ImplState->Context->InvalidateThreadCachedCodeRange(thread.Native, begin, size);
  return true;
}

EngineResult<bool> GuestEngine::DestroyThread(Thread*& thread) {
  if (ImplState == nullptr || ImplState->Context == nullptr || thread == nullptr || thread->Native == nullptr) {
    return Failure(EngineStage::Teardown, ESHUTDOWN);
  }
  {
    std::scoped_lock lock {ImplState->ThreadsMutex};
    if (!ImplState->Threads.contains(thread) || thread->Owner != std::this_thread::get_id()) {
      return Failure(EngineStage::Thread, EPERM);
    }
  }
  ImplState->Syscalls->UnregisterThread(thread->Native);
  ImplState->Context->DestroyThread(thread->Native);
  thread->Native = nullptr;
  {
    std::scoped_lock lock {ImplState->ThreadsMutex};
    ImplState->Threads.erase(thread);
  }
  delete thread;
  thread = nullptr;
  return true;
}

EngineResult<bool> GuestEngine::Shutdown() {
  if (ImplState == nullptr) return true;
  auto result = ImplState->Shutdown();
  if (std::holds_alternative<bool>(result)) {
    ImplState.reset();
  }
  return result;
}

std::uintptr_t GuestEngine::ReturnAddress() const {
  if (ImplState == nullptr || ImplState->FunctionReturn == nullptr) return 0;
  return reinterpret_cast<std::uintptr_t>(ImplState->FunctionReturn->Get());
}

GuestExecutionRange GuestEngine::ReturnRange() const {
  if (ImplState == nullptr || ImplState->FunctionReturn == nullptr) return {};
  return {reinterpret_cast<std::uintptr_t>(ImplState->FunctionReturn->Get()),
          ImplState->PageSize, true, false};
}

GuestExecutionRange GuestEngine::CallbackReturnRange() const {
  if (ImplState == nullptr || ImplState->CallbackReturn == nullptr) return {};
  return {reinterpret_cast<std::uintptr_t>(ImplState->CallbackReturn->Get()),
          ImplState->PageSize, true, false};
}

} // namespace Core::Fex
