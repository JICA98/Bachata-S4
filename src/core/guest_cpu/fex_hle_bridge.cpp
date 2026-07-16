// SPDX-License-Identifier: MIT

#include "fex_hle_bridge.h"

#include <cerrno>

namespace Core::GuestCpu {

HleGuestBridge::HleGuestBridge(HleCallRegistry& registry_, RangeValidator validator_,
                               void* validator_context_, FailureReporter reporter_,
                               void* reporter_context_, ExecutableRangeQuery executable_query_,
                               void* executable_query_context_)
    : registry{registry_}, validator{validator_}, validator_context{validator_context_},
      reporter{reporter_}, reporter_context{reporter_context_},
      executable_query{executable_query_}, executable_query_context{executable_query_context_} {}

Fex::EngineResult<bool> HleGuestBridge::Invoke(HleCallFrame& frame) {
    const auto adapter = registry.Find(frame.operation);
    if (adapter == nullptr) {
        const HleCallFailure failure{ENOSYS, "unregistered HLE operation"};
        Report(failure);
        return Fex::EngineFailure{Fex::EngineStage::Bridge, failure.error};
    }
    frame.validate_range = validator;
    frame.validate_context = validator_context;
    const auto result = adapter->Invoke(frame);
    if (const auto* failure = std::get_if<HleCallFailure>(&result)) {
        Report(*failure);
        return Fex::EngineFailure{Fex::EngineStage::Bridge, failure->error};
    }
    return true;
}

std::optional<GuestExecutionRange> HleGuestBridge::QueryExecutableRange(
    std::uintptr_t address) {
    if (executable_query == nullptr) {
        return std::nullopt;
    }
    return executable_query(executable_query_context, address);
}

void HleGuestBridge::Report(const HleCallFailure& failure) const {
    if (reporter != nullptr) {
        reporter(reporter_context, failure);
    }
}

} // namespace Core::GuestCpu
