// SPDX-License-Identifier: MIT
#pragma once

#include "core/fex/fex_guest_engine.h"
#include "hle_call_adapter.h"

#include <optional>

namespace Core::GuestCpu {

class HleGuestBridge final : public Fex::GuestBridge {
public:
    using RangeValidator = bool (*)(void* context, std::uintptr_t address, std::size_t size,
                                    bool writable);
    using ExecutableRangeQuery = std::optional<GuestExecutionRange> (*)(void* context,
                                                                        std::uintptr_t address);
    using FailureReporter = void (*)(void* context, const HleCallFailure& failure);

    HleGuestBridge(HleCallRegistry& registry_, RangeValidator validator_, void* validator_context_,
                   FailureReporter reporter_ = nullptr, void* reporter_context_ = nullptr,
                   ExecutableRangeQuery executable_query_ = nullptr,
                   void* executable_query_context_ = nullptr);

    Fex::EngineResult<bool> Invoke(HleCallFrame& frame) override;
    std::optional<GuestExecutionRange> QueryExecutableRange(std::uintptr_t address) override;

private:
    void Report(const HleCallFailure& failure) const;

    HleCallRegistry& registry;
    RangeValidator validator;
    void* validator_context;
    FailureReporter reporter;
    void* reporter_context;
    ExecutableRangeQuery executable_query;
    void* executable_query_context;
};

} // namespace Core::GuestCpu
