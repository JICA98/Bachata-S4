// SPDX-FileCopyrightText: Copyright 2026 shadPS4 Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include "common/logging/log.h"
#include "core/libraries/fios2/fios2.h"
#include "core/libraries/kernel/file_system.h"
#include "core/libraries/libs.h"

namespace Libraries::Fios2 {

struct FiosStat {
    s64 size;
    u64 access_date;
    u64 modification_date;
    u64 creation_date;
    u32 flags;
    u32 reserved;
    s64 uid;
    s64 gid;
    s64 dev;
    s64 ino;
    s64 mode;
};

s32 PS4_SYSV_ABI sceFiosInitialize(const void* parameters) {
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][Initialize] parameters={}", parameters);
    return 0;
}

s32 PS4_SYSV_ABI sceFiosFHOpenSync(const void* op_attr, s32* handle, const char* path,
                                   const void* open_params) {
    if (handle == nullptr || path == nullptr) {
        return -1;
    }
    const s32 fd = Kernel::posix_open(path, 0, 0);
    if (fd < 0) {
        LOG_ERROR(Lib_SysModule, "[FIOS-HLE][FHOpenSync] failed path='{}'", path);
        return fd;
    }
    *handle = fd;
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][FHOpenSync] path='{}' handle={} op_attr={} open_params={}",
             path, fd, op_attr, open_params);
    return 0;
}

s32 PS4_SYSV_ABI sceFiosFHOpenWithModeSync(const void* op_attr, s32* handle, const char* path,
                                           const void* open_params, u16 mode) {
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][FHOpenWithModeSync] path='{}' mode={:#o}", path, mode);
    return sceFiosFHOpenSync(op_attr, handle, path, open_params);
}

s64 PS4_SYSV_ABI sceFiosFHReadSync(const void* op_attr, s32 handle, void* buffer, s64 size) {
    const s64 read = Kernel::sceKernelRead(handle, buffer, static_cast<u64>(size));
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][FHReadSync] handle={} size={} read={} buffer={} op_attr={}",
             handle, size, read, buffer, op_attr);
    return read;
}

s64 PS4_SYSV_ABI sceFiosFHPreadSync(const void* op_attr, s32 handle, void* buffer, s64 size,
                                    s64 offset) {
    const s64 read = Kernel::sceKernelPread(handle, buffer, static_cast<u64>(size), offset);
    LOG_INFO(Lib_SysModule,
             "[FIOS-HLE][FHPreadSync] handle={} offset={:#x} size={} read={} buffer={} op_attr={}",
             handle, offset, size, read, buffer, op_attr);
    return read;
}

s32 PS4_SYSV_ABI sceFiosFHStatSync(const void* op_attr, s32 handle, FiosStat* stat) {
    if (stat == nullptr) {
        return -1;
    }
    Kernel::OrbisKernelStat kernel_stat{};
    const s32 result = Kernel::sceKernelFstat(handle, &kernel_stat);
    if (result >= 0) {
        *stat = {};
        stat->size = kernel_stat.st_size;
        stat->uid = kernel_stat.st_uid;
        stat->gid = kernel_stat.st_gid;
        stat->dev = kernel_stat.st_dev;
        stat->ino = kernel_stat.st_ino;
        stat->mode = kernel_stat.st_mode;
    }
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][FHStatSync] handle={} size={} result={} op_attr={}",
             handle, result >= 0 ? stat->size : -1, result, op_attr);
    return result;
}

s64 PS4_SYSV_ABI sceFiosFHGetSize(s32 handle) {
    Kernel::OrbisKernelStat stat{};
    const s32 result = Kernel::sceKernelFstat(handle, &stat);
    const s64 size = result >= 0 ? stat.st_size : result;
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][FHGetSize] handle={} size={}", handle, size);
    return size;
}

s64 PS4_SYSV_ABI sceFiosFHSeek(s32 handle, s64 offset, s32 whence) {
    const s64 result = Kernel::posix_lseek(handle, offset, whence);
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][FHSeek] handle={} offset={} whence={} result={}", handle,
             offset, whence, result);
    return result;
}

s32 PS4_SYSV_ABI sceFiosIsValidHandle(s32 handle) {
    Kernel::OrbisKernelStat stat{};
    const bool valid = Kernel::sceKernelFstat(handle, &stat) >= 0;
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][IsValidHandle] handle={} valid={}", handle, valid);
    return valid ? 1 : 0;
}

s32 PS4_SYSV_ABI sceFiosFHCloseSync(const void* op_attr, s32 handle) {
    const s32 result = Kernel::posix_close(handle);
    LOG_INFO(Lib_SysModule, "[FIOS-HLE][FHCloseSync] handle={} result={} op_attr={}", handle,
             result, op_attr);
    return result;
}

void RegisterLib(Core::Loader::SymbolsResolver* sym) {
    LIB_FUNCTION("wAKZ-det+yo", "libSceFios2", 1, "libSceFios2", sceFiosInitialize);
    LIB_FUNCTION("b44anV2D7K0", "libSceFios2", 1, "libSceFios2", sceFiosFHOpenSync);
    LIB_FUNCTION("w13Ojm7ON9o", "libSceFios2", 1, "libSceFios2", sceFiosFHOpenWithModeSync);
    LIB_FUNCTION("Bn2ZF4ZjeuQ", "libSceFios2", 1, "libSceFios2", sceFiosFHReadSync);
    LIB_FUNCTION("2m9+Opco-hk", "libSceFios2", 1, "libSceFios2", sceFiosFHPreadSync);
    LIB_FUNCTION("xP45eIntEis", "libSceFios2", 1, "libSceFios2", sceFiosFHStatSync);
    LIB_FUNCTION("FdjoqFQOlt0", "libSceFios2", 1, "libSceFios2", sceFiosFHGetSize);
    LIB_FUNCTION("xReSebwKApA", "libSceFios2", 1, "libSceFios2", sceFiosFHSeek);
    LIB_FUNCTION("8IGjwtnvYwI", "libSceFios2", 1, "libSceFios2", sceFiosIsValidHandle);
    LIB_FUNCTION("AOujSGqU+ms", "libSceFios2", 1, "libSceFios2", sceFiosFHCloseSync);
}

} // namespace Libraries::Fios2
