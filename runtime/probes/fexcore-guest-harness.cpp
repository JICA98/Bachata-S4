// SPDX-License-Identifier: MIT

#include "../../src/core/fex/fex_guest_engine.h"

#include <cerrno>
#include <cstdio>
#include <memory>
#include <variant>

namespace {

constexpr uint64_t kHarnessBridgeOperation = 0xB4C4'F001ULL;
constexpr uint64_t kHarnessBridgeArgument = 0x1020'3040'5060'7080ULL;
constexpr uint64_t kHarnessBridgeResult = 0x8877'6655'4433'2211ULL;
constexpr const char* kFexRevision = "f2b679f6028ce1c38875233aecfcf5d3f8ebecec";

class HarnessBridge final : public Core::Fex::GuestBridge {
public:
  Core::Fex::EngineResult<uint64_t> Invoke(uint64_t operation, uint64_t argument) override {
    if (operation != kHarnessBridgeOperation || argument != kHarnessBridgeArgument) {
      return Core::Fex::EngineFailure {Core::Fex::EngineStage::Bridge, ENOSYS};
    }
    return kHarnessBridgeResult;
  }
};

int Fail(const Core::Fex::EngineFailure& failure) {
  std::fprintf(stderr, "FEXCORE_GUEST_ENGINE_FAIL stage=%d error=%d\n", static_cast<int>(failure.Stage), failure.Error);
  return 1;
}

} // namespace

int main() {
  HarnessBridge bridge;
  auto engineResult = Core::Fex::GuestEngine::Create(bridge);
  if (const auto* failure = std::get_if<Core::Fex::EngineFailure>(&engineResult)) return Fail(*failure);
  auto engine = std::move(std::get<std::unique_ptr<Core::Fex::GuestEngine>>(engineResult));

  auto runResult = engine->RunControlledHarness();
  if (const auto* failure = std::get_if<Core::Fex::EngineFailure>(&runResult)) return Fail(*failure);
  const auto result = std::get<Core::Fex::GuestRunResult>(runResult);
  if (!result.Gpr || !result.Rflags || !result.Xmm || !result.Bridge || !result.Threads || !result.Tls || !result.Invalidation) {
    return Fail({Core::Fex::EngineStage::Execute, EPROTO});
  }

  auto shutdownResult = engine->Shutdown();
  if (const auto* failure = std::get_if<Core::Fex::EngineFailure>(&shutdownResult)) return Fail(*failure);
  std::printf("FEXCORE_GUEST_ENGINE_OK revision=%s gpr=ok rflags=ok xmm=ok bridge=ok threads=ok tls=ok invalidation=ok teardown=ok\n", kFexRevision);
  return 0;
}
