// SPDX-License-Identifier: MIT
#pragma once

#include <cstdint>
#include <memory>
#include <variant>

namespace Core::Fex {

enum class EngineStage {
  Config,
  Context,
  Mapping,
  Thread,
  Execute,
  Bridge,
  Invalidate,
  Teardown,
};

struct EngineFailure final {
  EngineStage Stage;
  int Error;
};

template <typename T>
using EngineResult = std::variant<T, EngineFailure>;

class GuestBridge {
public:
  virtual ~GuestBridge() = default;

  virtual EngineResult<uint64_t> Invoke(uint64_t operation, uint64_t argument) = 0;
};

struct GuestRunResult final {
  bool Gpr {};
  bool Rflags {};
  bool Xmm {};
  bool Bridge {};
  bool Threads {};
  bool Tls {};
  bool Invalidation {};
};

class GuestEngine final {
public:
  static EngineResult<std::unique_ptr<GuestEngine>> Create(GuestBridge& bridge);

  GuestEngine(const GuestEngine&) = delete;
  GuestEngine& operator=(const GuestEngine&) = delete;
  ~GuestEngine();

  EngineResult<GuestRunResult> RunControlledHarness();
  EngineResult<bool> Shutdown();

private:
  class Impl;

  explicit GuestEngine(std::unique_ptr<Impl> impl);

  std::unique_ptr<Impl> ImplState;
};

} // namespace Core::Fex
