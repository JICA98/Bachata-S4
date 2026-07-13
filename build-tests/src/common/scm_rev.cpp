// SPDX-FileCopyrightText: Copyright 2024 shadPS4 Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include <string>

#include "common/scm_rev.h"

namespace Common {

constexpr char g_version[]  = "0.16.1 WIP";
constexpr bool g_is_release = false;

constexpr char g_scm_rev[]         = "45149a12ca94cf26402f3e0fea3e410d35b0c3d8";
constexpr char g_scm_branch[]      = "main";
constexpr char g_scm_desc[]        = "v0.1.4-0-g45149a12-dirty";
constexpr char g_scm_remote_name[] = "origin";
constexpr char g_scm_remote_url[]  = "https://github.com/JICA98/Bachata-S4";
constexpr char g_scm_date[]        = "2026-07-12 19:20:55";

const std::string GetRemoteNameFromLink() {
    std::string remote_url(Common::g_scm_remote_url);
    std::string remote_host;
    try {
        if (remote_url.starts_with("http")) {
            if (*remote_url.rbegin() == '/') {
                remote_url.pop_back();
            }
            remote_host = remote_url.substr(19, remote_url.rfind('/') - 19);
        } else if (remote_url.starts_with("git@")) {
            auto after_comma_pos = remote_url.find(':') + 1, slash_pos = remote_url.find('/');
            remote_host = remote_url.substr(after_comma_pos, slash_pos - after_comma_pos);
        } else {
            remote_host = "unknown";
        }
    } catch (...) {
        remote_host = "unknown";
    }
    return remote_host;
}

} // namespace

