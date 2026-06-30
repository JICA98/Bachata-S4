// SPDX-FileCopyrightText: Copyright 2026 shadPS4 Emulator Project
// SPDX-License-Identifier: GPL-2.0-or-later

#include "platform/bachata/runtime_client.h"

#include <array>
#include <string>

#ifndef _WIN32
#include <sys/socket.h>
#include <unistd.h>
#endif

#include <gtest/gtest.h>

namespace {

#ifndef _WIN32
std::string ReadLine(int fd) {
    std::string line;
    char c = 0;
    while (::recv(fd, &c, 1, 0) == 1) {
        line.push_back(c);
        if (c == '\n') {
            break;
        }
    }
    return line;
}
#endif

} // namespace

namespace Platform::Bachata {

TEST(BachataRuntimeClient, ParsesVersionHandshake) {
    const auto frame = ParseRuntimeFrame("BACHATA/1 HELLO version=1\n");

    ASSERT_TRUE(frame.has_value());
    EXPECT_EQ(frame->event, RuntimeEvent::Hello);
    EXPECT_EQ(frame->version, RuntimeProtocolVersion);
}

TEST(BachataRuntimeClient, ParsesLifecycleEvents) {
    const auto starting = ParseRuntimeFrame("BACHATA/1 EVENT Starting\n");
    const auto running = ParseRuntimeFrame("BACHATA/1 EVENT Running\n");
    const auto stopped = ParseRuntimeFrame("BACHATA/1 EVENT Stopped exit_code=7\n");

    ASSERT_TRUE(starting.has_value());
    ASSERT_TRUE(running.has_value());
    ASSERT_TRUE(stopped.has_value());
    EXPECT_EQ(starting->event, RuntimeEvent::Starting);
    EXPECT_EQ(running->event, RuntimeEvent::Running);
    EXPECT_EQ(stopped->event, RuntimeEvent::Stopped);
    EXPECT_EQ(stopped->exit_code, 7);
}

TEST(BachataRuntimeClient, RejectsMalformedFrames) {
    EXPECT_FALSE(ParseRuntimeFrame("BACHATA/2 HELLO version=2\n").has_value());
    EXPECT_FALSE(ParseRuntimeFrame("BACHATA/1 EVENT Stopped\n").has_value());
    EXPECT_FALSE(ParseRuntimeFrame("BACHATA/1 EVENT Unknown\n").has_value());
    EXPECT_FALSE(ParseRuntimeFrame("garbage\n").has_value());
}

TEST(BachataRuntimeClient, ValidatesSocketPathUnderRuntimeRoot) {
    EXPECT_TRUE(
        ValidateSocketPath("/data/user/0/app/runtime/bachata.sock", "/data/user/0/app/runtime"));
    EXPECT_FALSE(ValidateSocketPath("runtime/bachata.sock", "/data/user/0/app/runtime"));
    EXPECT_FALSE(ValidateSocketPath("/data/user/0/app/other.sock", "/data/user/0/app/runtime"));
}

TEST(BachataRuntimeClient, DisabledClientIsNoOp) {
    auto client = RuntimeClient::Disabled();

    EXPECT_FALSE(client.IsEnabled());
    EXPECT_TRUE(client.SendHello());
    EXPECT_TRUE(client.SendStarting());
    EXPECT_TRUE(client.SendRunning());
    EXPECT_TRUE(client.SendStopped(0));
    EXPECT_TRUE(client.SendError("CONTENT_INVALID"));
}

#ifndef _WIN32
TEST(BachataRuntimeClient, SendsLifecycleFramesOverSocketPair) {
    std::array<int, 2> fds{};
    ASSERT_EQ(::socketpair(AF_UNIX, SOCK_STREAM, 0, fds.data()), 0);

    RuntimeClient client(fds[0]);
    ASSERT_TRUE(client.SendHello());
    ASSERT_TRUE(client.SendStarting());
    ASSERT_TRUE(client.SendRunning());
    ASSERT_TRUE(client.SendStopped(3));

    EXPECT_EQ(ReadLine(fds[1]), "BACHATA/1 HELLO version=1\n");
    EXPECT_EQ(ReadLine(fds[1]), "BACHATA/1 EVENT Starting\n");
    EXPECT_EQ(ReadLine(fds[1]), "BACHATA/1 EVENT Running\n");
    EXPECT_EQ(ReadLine(fds[1]), "BACHATA/1 EVENT Stopped exit_code=3\n");
    ::close(fds[1]);
}

TEST(BachataRuntimeClient, ReportsDisconnectedPeer) {
    std::array<int, 2> fds{};
    ASSERT_EQ(::socketpair(AF_UNIX, SOCK_STREAM, 0, fds.data()), 0);
    ::close(fds[1]);

    RuntimeClient client(fds[0]);
    EXPECT_FALSE(client.SendStarting());
}
#endif

} // namespace Platform::Bachata
