import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (relative) => readFileSync(resolve(root, relative), "utf8");

test("Sonic packages and selects the native ARM64 FEX runtime without replacing Box64", () => {
  const build = read("runtime/scripts/build-runtime-debian.sh");
  const arm64Build = read("runtime/scripts/build-shadps4-arm64.sh");
  const dependencies = read("runtime/scripts/install-debian-runtime-deps.sh");
  const pack = read("runtime/scripts/package-runtime.mjs");
  const verify = read("runtime/tests/verify-runtime.mjs");
  const profile = read("android/BachataS4/app/src/main/kotlin/com/bachatas4/android/RuntimeLaunchProfileProvider.kt");
  const launcher = read("android/BachataS4/core/runtime/src/main/kotlin/com/bachatas4/android/runtime/process/RuntimeProcessLauncher.kt");
  const service = read("android/BachataS4/app/src/main/kotlin/com/bachatas4/android/service/EmulationService.kt");
  const libcMemory = read("src/core/libraries/libc_internal/libc_internal_memory.cpp");
  const libcInternal = read("src/core/libraries/libc_internal/libc_internal.cpp");
  const libcMath = read("src/core/libraries/libc_internal/libc_internal_math.cpp");
  const libcIo = read("src/core/libraries/libc_internal/libc_internal_io.cpp");
  const libcStr = read("src/core/libraries/libc_internal/libc_internal_str.cpp");
  const libcCxa = read("src/core/libraries/libc_internal/libc_internal_cxa.cpp");
  const audioOut = read("src/core/libraries/audio/audioout.cpp");
  const linker = read("src/core/linker.cpp");

  assert.match(build, /build-shadps4-arm64\.sh/);
  assert.match(dependencies, /libx11-dev:arm64/);
  assert.match(dependencies, /libxext-dev:arm64/);
  assert.match(arm64Build, /XEXT_LIB:FILEPATH/);
  assert.match(arm64Build, /SDL_VIDEO_DRIVER_X11_DYNAMIC_XEXT/);
  assert.match(arm64Build, /libuuid\.so\.1/);
  assert.match(arm64Build, /libudev\.so\.1/);
  assert.match(arm64Build, /CMAKE_EXE_LINKER_FLAGS/);
  assert.match(pack, /shadps4-arm64-fex/);
  assert.match(verify, /host\/shadps4-arm64-fex/);
  assert.match(profile, /CUSA07023/);
  assert.match(profile, /RuntimeGuestBackend\.FEX/);
  assert.match(launcher, /RuntimeGuestBackend/);
  assert.match(service, /guestBackend/);
  assert.match(service, /host\/shadps4-arm64-fex/);
  assert.match(pack, /bin\/shadps4/);
  assert.match(launcher, /RuntimeGuestBackend\.BOX64/);
  assert.match(libcMemory, /void RegisterFexLibcMemoryAliases\(Core::Loader::SymbolsResolver\* sym\)/);
  assert.match(libcInternal, /#ifdef SHADPS4_ENABLE_FEX_GUEST_CPU[\s\S]*RegisterFexLibcMemoryAliases\(sym\);[\s\S]*RegisterFexLibcMathAliases\(sym\);[\s\S]*RegisterFexLibcStrAliases\(sym\);[\s\S]*RegisterFexLibcCxaAliases\(sym\);[\s\S]*#endif/);
  assert.doesNotMatch(libcInternal, /RegisterFexLibcIoAliases\(sym\)/);
  const fexAliases = libcMemory.slice(libcMemory.indexOf("#ifdef SHADPS4_ENABLE_FEX_GUEST_CPU"));
  assert.doesNotMatch(fexAliases, /fex_libc_(?:init_env|allocate|operator_new|malloc|free|rand)/);
  assert.doesNotMatch(fexAliases, /LIB_FUNCTION\("(?:bzQExy189ZI|fJnpuVVBbKk|gQX\+4GDQjpM|tIhsqj0qsFE|z\+P\+xCnWLBk|cpCOXWMgha0)"/);
  assert.match(fexAliases, /LIB_FUNCTION\("Q3VBxCXhUHs", "libc", 1, "libc", internal_memcpy\)/);
  assert.match(fexAliases, /LIB_FUNCTION\("8zTFvBIAIN8", "libc", 1, "libc", internal_memset\)/);
  assert.match(libcMath, /float PS4_SYSV_ABI fex_libc_fsin\(float arg, u32 m, s32 n\)/);
  for (const nid of ["ZtjspkJQ\\+vw", "ZE6RNL\\+eLbk", "GZWjF-YIFFk", "QI-x0SL8jhw", "EH-x713A99c"])
    assert.match(libcMath, new RegExp(`LIB_FUNCTION\\("${nid}", "libc", 1, "libc",`));
  assert.doesNotMatch(libcIo, /fex_libc_(?:fopen|fclose|ftell)/);
  for (const nid of ["xeYO4u7uyJ0", "rQFVBXp-Cxg", "Qazy8LmXTvw", "lbB\\+UlZqVG0"])
    assert.doesNotMatch(libcIo, new RegExp(`LIB_FUNCTION\\("${nid}", "libc", 1, "libc",`));
  assert.match(libcStr, /char\* PS4_SYSV_ABI internal_strcpy\(char\* dest, const char\* src\)/);
  for (const nid of ["kiZSXIWd9vg", "Ls4tzzhimqQ", "j4ViWNHEgww", "Ovb2dSJOAuE"])
    assert.match(libcStr, new RegExp(`LIB_FUNCTION\\("${nid}", "libc", 1, "libc",`));
  assert.match(libcCxa, /int PS4_SYSV_ABI fex_libc_cxa_guard_acquire\(u64\* guard_object\)/);
  assert.match(libcCxa, /std::condition_variable GuardCondition/);
  for (const nid of ["3GPpjQdAMTw", "9rAeANT2tyE", "2emaaluWzUw"])
    assert.match(libcCxa, new RegExp(`LIB_FUNCTION\\("${nid}", "libc", 1, "libc",`));
  assert.match(audioOut, /s32 PS4_SYSV_ABI fex_sceAudioOutOpen\([\s\S]*u32 param_raw\)/);
  assert.match(audioOut, /static_assert\(sizeof\(param_type\) == sizeof\(param_raw\)\)/);
  assert.match(audioOut, /LIB_FUNCTION\("ekNvsT22rsY", "libSceAudioOut", 1, "libSceAudioOut",[\s\S]*fex_sceAudioOutOpen\)/);
  assert.match(linker, /void\* Linker::CallAppHeapMalloc\(u64 size\)/);
  assert.match(linker, /RunGuestFunction\(reinterpret_cast<VAddr>\(heap_api->heap_malloc\), arguments\)/);
  assert.match(linker, /void Linker::CallAppHeapFree\(void\* pointer\)/);
  assert.match(linker, /RunGuestFunction\(reinterpret_cast<VAddr>\(heap_api->heap_free\), arguments\)/);
  assert.match(linker, /u8\* dest = reinterpret_cast<u8\*>\(CallAppHeapMalloc\(module->tls.image_size\)\)/);
  assert.match(linker, /addr_out = CallAppHeapMalloc\(total_tls_size\)/);
  assert.match(linker, /CallAppHeapFree\(pointer\)/);
  assert.doesNotMatch(fexAliases, /AddUnsupported/);
});
