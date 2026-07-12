// SPDX-License-Identifier: MIT

#include "Common/Config.h"
#include "Common/HostFeatures.h"
#include "DummyHandlers.h"

#include <FEXCore/Config/Config.h>
#include <FEXCore/Core/Context.h>
#include <FEXCore/Core/CoreState.h>
#include <FEXCore/Core/X86Enums.h>
#include <FEXCore/Debug/InternalThreadState.h>
#include <FEXCore/HLE/SyscallHandler.h>

#include <sys/mman.h>
#include <unistd.h>

#include <array>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <string_view>
#include <vector>

namespace {
constexpr long kRequiredPageSize = 4096;
constexpr uint64_t kAddLeft = 0x1122'3344'5566'7788ULL;
constexpr uint64_t kAddRight = 0x0102'0304'0506'0708ULL;
constexpr uint64_t kXorLeft = 0xfedc'ba98'7654'3210ULL;
constexpr uint64_t kXorRight = 0x0f0f'0f0f'0f0f'0f0fULL;
constexpr uint64_t kStackSentinel = 0xcafe'babe'dead'beefULL;
constexpr double kFpLeft = 1.5;
constexpr double kFpRight = 2.25;
constexpr double kFpResult = 3.75;
constexpr std::string_view kFexRevision =
  "f2b679f6028ce1c38875233aecfcf5d3f8ebecec";

int Fail(std::string_view check) {
  std::fprintf(stderr, "FEXCORE_SMOKE_FAIL check=%.*s\n", static_cast<int>(check.size()), check.data());
  return 1;
}

class Mapping final {
public:
  Mapping(size_t size, int protection)
    : Size {size}
    , Address {mmap(nullptr, size, protection, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0)} {}

  Mapping(const Mapping&) = delete;
  Mapping& operator=(const Mapping&) = delete;

  ~Mapping() {
    if (Address != MAP_FAILED) {
      munmap(Address, Size);
    }
  }

  bool IsValid() const {
    return Address != MAP_FAILED;
  }

  void* Get() const {
    return Address;
  }

private:
  size_t Size;
  void* Address;
};

class ConfigScope final {
public:
  ConfigScope() {
    FEX::Config::InitializeConfigs(FEX::Config::PortableInformation {});
    FEXCore::Config::Initialize();
    FEXCore::Config::Load();
    FEXCore::Config::Set(FEXCore::Config::CONFIG_IS64BIT_MODE, "1");
    FEXCore::Config::Set(FEXCore::Config::CONFIG_DISABLETELEMETRY, "1");
  }

  ConfigScope(const ConfigScope&) = delete;
  ConfigScope& operator=(const ConfigScope&) = delete;

  ~ConfigScope() {
    FEXCore::Config::Shutdown();
  }
};

class SmokeSyscallHandler final : public FEXCore::HLE::SyscallHandler {
public:
  SmokeSyscallHandler() {
    OSABI = FEXCore::HLE::SyscallOSABI::OS_GENERIC;
  }

  uint64_t HandleSyscall(FEXCore::Core::CpuStateFrame*, FEXCore::HLE::SyscallArguments*) override {
    return 0;
  }

  FEXCore::HLE::ExecutableRangeInfo QueryGuestExecutableRange(FEXCore::Core::InternalThreadState*, uint64_t) override {
    return {0, std::numeric_limits<uint64_t>::max(), true};
  }

  std::optional<FEXCore::ExecutableFileSectionInfo>
  LookupExecutableFileSection(FEXCore::Core::InternalThreadState*, uint64_t) override {
    return std::nullopt;
  }
};

class ThreadScope final {
public:
  ThreadScope(FEXCore::Context::Context* context, FEXCore::Core::InternalThreadState* thread)
    : Context {context}
    , Thread {thread} {}

  ThreadScope(const ThreadScope&) = delete;
  ThreadScope& operator=(const ThreadScope&) = delete;

  ~ThreadScope() {
    if (Thread != nullptr) {
      Context->DestroyThread(Thread);
    }
  }

private:
  FEXCore::Context::Context* Context;
  FEXCore::Core::InternalThreadState* Thread;
};

void AppendImmediate(std::vector<uint8_t>& code, uint64_t value) {
  const auto offset = code.size();
  code.resize(offset + sizeof(value));
  std::memcpy(code.data() + offset, &value, sizeof(value));
}

std::vector<uint8_t> BuildGuestCode() {
  std::vector<uint8_t> code;

  code.insert(code.end(), {0x48, 0xb8}); // mov rax, kAddLeft
  AppendImmediate(code, kAddLeft);
  code.insert(code.end(), {0x48, 0xbb}); // mov rbx, kAddRight
  AppendImmediate(code, kAddRight);
  code.insert(code.end(), {0x48, 0x01, 0xd8}); // add rax, rbx

  code.insert(code.end(), {0x48, 0xb9}); // mov rcx, kXorLeft
  AppendImmediate(code, kXorLeft);
  code.insert(code.end(), {0x48, 0xba}); // mov rdx, kXorRight
  AppendImmediate(code, kXorRight);
  code.insert(code.end(), {0x48, 0x31, 0xd1}); // xor rcx, rdx

  code.insert(code.end(), {0xf2, 0x0f, 0x58, 0xc1}); // addsd xmm0, xmm1

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
  return code;
}

uint64_t DoubleBits(double value) {
  uint64_t bits;
  std::memcpy(&bits, &value, sizeof(bits));
  return bits;
}
} // namespace

int main() {
  const long pageSize = sysconf(_SC_PAGESIZE);
  if (pageSize != kRequiredPageSize) {
    std::fprintf(stderr, "FEXCORE_SMOKE_FAIL check=page-size expected=4096 actual=%ld\n", pageSize);
    return 2;
  }

  Mapping codePage {static_cast<size_t>(pageSize), PROT_READ | PROT_WRITE | PROT_EXEC};
  if (!codePage.IsValid()) return Fail("map-code");
  Mapping stackPage {static_cast<size_t>(pageSize), PROT_READ | PROT_WRITE};
  if (!stackPage.IsValid()) return Fail("map-stack");
  Mapping tlsPage {static_cast<size_t>(pageSize), PROT_READ | PROT_WRITE};
  if (!tlsPage.IsValid()) return Fail("map-tls");

  const auto guestCode = BuildGuestCode();
  if (guestCode.size() > static_cast<size_t>(pageSize)) return Fail("guest-code-size");
  std::memcpy(codePage.Get(), guestCode.data(), guestCode.size());

  ConfigScope config;
  auto hostFeatures = FEX::FetchHostFeatures();
  auto context = FEXCore::Context::Context::CreateNewContext(hostFeatures);
  if (!context) return Fail("create-context");
  auto signalDelegator = FEX::DummyHandlers::CreateSignalDelegator();
  auto syscallHandler = fextl::make_unique<SmokeSyscallHandler>();

  context->SetSignalDelegator(signalDelegator.get());
  context->SetSyscallHandler(syscallHandler.get());
  context->EnableExitOnHLT();
  if (!context->InitCore()) return Fail("init-core");

  const uint64_t initialRip = reinterpret_cast<uint64_t>(codePage.Get());
  const uint64_t initialRsp = reinterpret_cast<uint64_t>(stackPage.Get()) + pageSize - 16;
  auto* thread = context->CreateThread(initialRip, initialRsp);
  if (thread == nullptr) return Fail("create-thread");
  ThreadScope threadScope {context.get(), thread};
  thread->CurrentFrame->State.fs_cached = reinterpret_cast<uint64_t>(tlsPage.Get());

  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> initialXmm {};
  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> initialYmmHigh {};
  const uint64_t fpLeftBits = DoubleBits(kFpLeft);
  const uint64_t fpRightBits = DoubleBits(kFpRight);
  std::memcpy(&initialXmm[0], &fpLeftBits, sizeof(fpLeftBits));
  std::memcpy(&initialXmm[1], &fpRightBits, sizeof(fpRightBits));
  context->SetXMMRegistersFromState(thread, initialXmm.data(), hostFeatures.SupportsAVX ? initialYmmHigh.data() : nullptr);

  context->ExecuteThread(thread);

  const auto& state = thread->CurrentFrame->State;
  if (state.gregs[FEXCore::X86State::REG_RAX] != kAddLeft + kAddRight ||
      state.gregs[FEXCore::X86State::REG_RCX] != (kXorLeft ^ kXorRight)) {
    return Fail("gpr");
  }
  if (state.gregs[FEXCore::X86State::REG_RSI] != kStackSentinel || state.gregs[FEXCore::X86State::REG_RDI] != kStackSentinel ||
      state.gregs[FEXCore::X86State::REG_RSP] != initialRsp) {
    return Fail("stack");
  }

  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> finalXmm {};
  std::array<__uint128_t, FEXCore::Core::CPUState::NUM_XMMS> finalYmmHigh {};
  context->ReconstructXMMRegisters(thread, finalXmm.data(), hostFeatures.SupportsAVX ? finalYmmHigh.data() : nullptr);
  uint64_t fpResultBits;
  std::memcpy(&fpResultBits, &finalXmm[0], sizeof(fpResultBits));
  if (fpResultBits != DoubleBits(kFpResult)) return Fail("fp");

  std::printf("FEXCORE_SMOKE_BOOTSTRAP_OK revision=%.*s gpr=ok stack=ok fp=ok\n", static_cast<int>(kFexRevision.size()),
              kFexRevision.data());
  return 0;
}
