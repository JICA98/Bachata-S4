// SPDX-FileCopyrightText: Copyright 2024 shadPS4 Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <algorithm>
#include <cstdlib>

#include "common/assert.h"
#include "common/logging/log.h"
#include "core/libraries/error_codes.h"
#include "core/libraries/libs.h"
#include "libc_internal_memory.h"
#ifdef SHADPS4_ENABLE_FEX_GUEST_CPU
#include "core/guest_cpu/hle_call_adapter.h"
#endif

namespace Libraries::LibcInternal {

#ifdef SHADPS4_ENABLE_FEX_GUEST_CPU
void PS4_SYSV_ABI fex_libc_init_env() {}

void* fex_libc_allocate(u64 count) {
    void* ptr = std::malloc(std::max<u64>(count, 1));
    if (ptr != nullptr &&
        !Core::GuestCpu::PublishHostRange(ptr, std::max<u64>(count, 1), true)) {
        std::free(ptr);
        return nullptr;
    }
    return ptr;
}

void* PS4_SYSV_ABI fex_libc_operator_new(u64 count) {
    return fex_libc_allocate(count);
}

void* PS4_SYSV_ABI fex_libc_malloc(u64 count) {
    return fex_libc_allocate(count);
}

void PS4_SYSV_ABI fex_libc_free(void* ptr) {
    if (ptr == nullptr) {
        return;
    }
    if (!Core::GuestCpu::RevokeHostRange(ptr)) {
        LOG_ERROR(Lib_LibcInternal, "refusing to free an unpublished FEX libc pointer {}", ptr);
        return;
    }
    std::free(ptr);
}

s32 PS4_SYSV_ABI fex_libc_rand() {
    return std::rand();
}
#endif

void* PS4_SYSV_ABI internal_memset(void* s, int c, size_t n) {
    return std::memset(s, c, n);
}

void* PS4_SYSV_ABI internal_memcpy(void* dest, const void* src, size_t n) {
    return std::memcpy(dest, src, n);
}

s32 PS4_SYSV_ABI internal_memcpy_s(void* dest, size_t destsz, const void* src, size_t count) {
#ifdef _WIN64
    return memcpy_s(dest, destsz, src, count);
#else
    std::memcpy(dest, src, count);
    return 0; // ALL OK
#endif
}

s32 PS4_SYSV_ABI internal_memcmp(const void* s1, const void* s2, size_t n) {
    return std::memcmp(s1, s2, n);
}

#ifdef SHADPS4_ENABLE_FEX_GUEST_CPU
void RegisterFexLibcMemoryAliases(Core::Loader::SymbolsResolver* sym) {
    LIB_FUNCTION("bzQExy189ZI", "libc", 1, "libc", fex_libc_init_env);
    LIB_FUNCTION("fJnpuVVBbKk", "libc", 1, "libc", fex_libc_operator_new);
    LIB_FUNCTION("gQX+4GDQjpM", "libc", 1, "libc", fex_libc_malloc);
    LIB_FUNCTION("tIhsqj0qsFE", "libc", 1, "libc", fex_libc_free);
    LIB_FUNCTION("z+P+xCnWLBk", "libc", 1, "libc", fex_libc_free);
    LIB_FUNCTION("cpCOXWMgha0", "libc", 1, "libc", fex_libc_rand);
    LIB_FUNCTION("Q3VBxCXhUHs", "libc", 1, "libc", internal_memcpy);
    LIB_FUNCTION("8zTFvBIAIN8", "libc", 1, "libc", internal_memset);
    LIB_FUNCTION("DfivPArhucg", "libc", 1, "libc", internal_memcmp);
}
#endif

void RegisterlibSceLibcInternalMemory(Core::Loader::SymbolsResolver* sym) {

    LIB_FUNCTION("NFLs+dRJGNg", "libSceLibcInternal", 1, "libSceLibcInternal", internal_memcpy_s);
    LIB_FUNCTION("Q3VBxCXhUHs", "libSceLibcInternal", 1, "libSceLibcInternal", internal_memcpy);
    LIB_FUNCTION("8zTFvBIAIN8", "libSceLibcInternal", 1, "libSceLibcInternal", internal_memset);
    LIB_FUNCTION("DfivPArhucg", "libSceLibcInternal", 1, "libSceLibcInternal", internal_memcmp);
}

} // namespace Libraries::LibcInternal
