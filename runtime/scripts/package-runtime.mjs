#!/usr/bin/env node

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { chmodSync, copyFileSync, mkdirSync, readdirSync, readFileSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import { basename, dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

function fail(message) {
  throw new Error(message);
}

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function run(command, args) {
  execFileSync(command, args, { stdio: "inherit" });
}

function copy(source, target, mode) {
  mkdirSync(dirname(target), { recursive: true });
  copyFileSync(source, target);
  chmodSync(target, mode);
}

function patchFixedString(file, original, replacement) {
  const bytes = readFileSync(file);
  const source = Buffer.from(original);
  const target = Buffer.from(replacement);
  if (target.length > source.length) fail(`Replacement is too long for ${file}`);
  const offset = bytes.indexOf(source);
  if (offset < 0 || bytes.indexOf(source, offset + 1) >= 0) fail(`Expected one ${original} string in ${file}`);
  bytes.fill(0, offset, offset + source.length);
  target.copy(bytes, offset);
  writeFileSync(file, bytes);
}

function disableArm64SetRobustList(file, expectedCount) {
  const bytes = readFileSync(file);
  let patched = 0;
  for (let offset = 0; offset + 4 <= bytes.length; offset += 4) {
    if (bytes.readUInt32LE(offset) !== 0xd2800c68) continue; // mov x8, #99
    for (let syscallOffset = offset + 4; syscallOffset <= offset + 32; syscallOffset += 4) {
      if (bytes.readUInt32LE(syscallOffset) !== 0xd4000001) continue; // svc #0
      bytes.writeUInt32LE(0xd2800000, syscallOffset); // mov x0, #0
      patched++;
      break;
    }
  }
  if (patched !== expectedCount) fail(`Unexpected set_robust_list call count in ${file}: ${patched}`);
  writeFileSync(file, bytes);
}

function disableArm64Clone3(file, expectedCount) {
  const bytes = readFileSync(file);
  let patched = 0;
  for (let offset = 0; offset + 8 <= bytes.length; offset += 4) {
    if (bytes.readUInt32LE(offset) !== 0xd2803668 || bytes.readUInt32LE(offset + 4) !== 0xd4000001) continue;
    bytes.writeUInt32LE(0x928004a0, offset + 4); // mov x0, #-38 (ENOSYS), forcing clone fallback
    patched++;
  }
  if (patched !== expectedCount) fail(`Unexpected clone3 call count in ${file}: ${patched}`);
  writeFileSync(file, bytes);
}

function disableArm64Faccessat2(file, expectedCount) {
  const bytes = readFileSync(file);
  let patched = 0;
  for (let offset = 0; offset + 8 <= bytes.length; offset += 4) {
    if (bytes.readUInt32LE(offset) !== 0xd28036e8 || bytes.readUInt32LE(offset + 4) !== 0xd4000001) continue;
    bytes.writeUInt32LE(0x928004a0, offset + 4); // mov x0, #-38 (ENOSYS), forcing faccessat fallback
    patched++;
  }
  if (patched !== expectedCount) fail(`Unexpected faccessat2 call count in ${file}: ${patched}`);
  writeFileSync(file, bytes);
}

function collectFiles(root, directory = root, files = []) {
  for (const name of readdirSync(directory).sort()) {
    const absolute = join(directory, name);
    const stats = statSync(absolute);
    if (stats.isDirectory()) collectFiles(root, absolute, files);
    else if (stats.isFile()) {
      const path = relative(root, absolute).split(sep).join("/");
      if (!path || path.startsWith("../") || path.includes("/../")) fail(`Unsafe runtime path: ${path}`);
      files.push({ path, bytes: readFileSync(absolute) });
    } else fail(`Unsupported runtime entry: ${absolute}`);
  }
  return files;
}

function localHeader(entry) {
  const name = Buffer.from(entry.path, "utf8");
  const header = Buffer.alloc(30);
  header.writeUInt32LE(0x04034b50, 0);
  header.writeUInt16LE(20, 4);
  header.writeUInt16LE(0x0800, 6);
  header.writeUInt16LE(0, 8);
  header.writeUInt16LE(0, 10);
  header.writeUInt16LE(0, 12);
  header.writeUInt32LE(entry.crc, 14);
  header.writeUInt32LE(entry.bytes.length, 18);
  header.writeUInt32LE(entry.bytes.length, 22);
  header.writeUInt16LE(name.length, 26);
  return Buffer.concat([header, name, entry.bytes]);
}

function centralHeader(entry) {
  const name = Buffer.from(entry.path, "utf8");
  const header = Buffer.alloc(46);
  header.writeUInt32LE(0x02014b50, 0);
  header.writeUInt16LE(0x0314, 4);
  header.writeUInt16LE(20, 6);
  header.writeUInt16LE(0x0800, 8);
  header.writeUInt16LE(0, 10);
  header.writeUInt16LE(0, 12);
  header.writeUInt16LE(0, 14);
  header.writeUInt32LE(entry.crc, 16);
  header.writeUInt32LE(entry.bytes.length, 20);
  header.writeUInt32LE(entry.bytes.length, 24);
  header.writeUInt16LE(name.length, 28);
  header.writeUInt32LE(0x81a40000, 38);
  header.writeUInt32LE(entry.offset, 42);
  return Buffer.concat([header, name]);
}

function makeZip(files) {
  let offset = 0;
  const entries = files.map(({ path, bytes }) => {
    if (bytes.length > 0xffffffff) fail(`ZIP64 is not supported: ${path}`);
    const entry = { path, bytes, crc: crc32(bytes), offset };
    offset += 30 + Buffer.byteLength(path) + bytes.length;
    return entry;
  });
  const local = entries.map(localHeader);
  const central = entries.map(centralHeader);
  const centralSize = central.reduce((sum, part) => sum + part.length, 0);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralSize, 12);
  eocd.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, ...central, eocd]);
}

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, "../..");
if (process.argv.length > 3) fail("usage: package-runtime.mjs [output-directory]");
const rootfs = resolve(projectRoot, "runtime/build/rootfs");
const extractDir = resolve(projectRoot, "runtime/build/extract");
const inputDir = resolve(projectRoot, "runtime/build/inputs");
const shadps4StageDir = resolve(projectRoot, "runtime/build/shadps4-stage");
const hostBox64Binary = resolve(projectRoot, "runtime/build/box64-host-stage/box64");
const fexcoreSmokeBinary = resolve(
  projectRoot,
  "runtime/build/fexcore-smoke-stage/bin/fexcore-smoke",
);
const outputDir = resolve(process.argv[2] ?? resolve(projectRoot, "android/BachataS4/app/src/main/assets/runtime"));
const nativeOutputDir = resolve(projectRoot, "android/BachataS4/core/runtime/src/main/jniLibs/arm64-v8a");
const componentLock = JSON.parse(readFileSync(resolve(projectRoot, "runtime/locks/components.lock.json"), "utf8"));
const inputLock = JSON.parse(readFileSync(resolve(projectRoot, "runtime/locks/runtime-inputs.lock.json"), "utf8"));
const revisions = Object.fromEntries(componentLock.components.map(({ name, revision }) => [name, revision]));
const fexcoreSmokeStats = statSync(fexcoreSmokeBinary, { throwIfNoEntry: false });
if (!fexcoreSmokeStats?.isFile()) fail(`Missing FEXCore smoke runner: ${fexcoreSmokeBinary}`);
for (const input of inputLock.inputs) {
  const bytes = readFileSync(join(inputDir, input.name));
  if (sha256(bytes) !== input.sha256) fail(`Locked input hash mismatch: ${input.name}`);
}
for (const component of ["glibc-packages", "winlator-app", "winlator-components"]) {
  const sourceDir = resolve(projectRoot, `runtime/sources/${component}`);
  const actual = execFileSync("git", ["-C", sourceDir, "rev-parse", "HEAD"], { encoding: "utf8" }).trim();
  if (actual !== revisions[component]) fail(`Source revision mismatch: ${component}`);
}
const patchRoot = resolve(projectRoot, "runtime/sources/winlator-components/glibc_patches");
for (const patch of inputLock.winlatorGlibcPatches) {
  if (sha256(readFileSync(join(patchRoot, patch.path))) !== patch.sha256) fail(`Winlator glibc patch mismatch: ${patch.path}`);
}

rmSync(rootfs, { recursive: true, force: true });
rmSync(extractDir, { recursive: true, force: true });
const glibcExtract = join(extractDir, "glibc");
const glibcArmExtract = join(extractDir, "glibc-arm64");
const libstdcppExtract = join(extractDir, "libstdcpp");
const libgccExtract = join(extractDir, "libgcc");
const certificatesExtract = join(extractDir, "certificates");
const sdlExtract = join(extractDir, "sdl2");
const guestX11Packages = [
  "libx11-",
  "libxext-",
  "libxcursor-",
  "libxrandr-",
  "libxrender-",
  "libxfixes-",
  "libxi-",
  "libxss-1.2.5-1-x86_64",
  "libxcb-",
  "libxau-",
  "libxdmcp-",
  "libxkbcommon-",
];
const guestX11Extracts = new Map(guestX11Packages.map((prefix) => [prefix, join(extractDir, `guest-${prefix.replace(/[^a-z0-9]/gi, "_")}`)]));
const libxssExtract = join(extractDir, "libxss");
const libxkbcommonHostExtract = join(extractDir, "libxkbcommon-host");
const dbusExtract = join(extractDir, "dbus");
const systemdExtract = join(extractDir, "systemd-libs");
const systemdGuestExtract = join(extractDir, "systemd-libs-x86_64");
const utilLinuxGuestExtract = join(extractDir, "util-linux-libs-x86_64");
const hostExtract = join(extractDir, "host");
const vulkanArmExtract = join(extractDir, "vulkan-arm64");
const vulkanGuestExtract = join(extractDir, "vulkan-x86_64");
const armStdcppExtract = join(extractDir, "libstdcpp-arm64");
const armZlibExtract = join(extractDir, "zlib-arm64");
const armDrmExtract = join(extractDir, "libdrm-arm64");
for (const directory of [rootfs, glibcExtract, glibcArmExtract, libstdcppExtract, libgccExtract, certificatesExtract, sdlExtract, ...guestX11Extracts.values(), libxssExtract, libxkbcommonHostExtract, dbusExtract, systemdExtract, systemdGuestExtract, utilLinuxGuestExtract, hostExtract, vulkanArmExtract, vulkanGuestExtract, armStdcppExtract, armZlibExtract, armDrmExtract]) mkdirSync(directory, { recursive: true });
const inputByPrefix = (prefix) => join(inputDir, inputLock.inputs.find(({ name }) => name.startsWith(prefix)).name);
run("tar", ["--zstd", "-xf", inputByPrefix("glibc-2.43+r22+g8362e8ce10b2-2-x86_64"), "-C", glibcExtract]);
run("tar", ["-xf", inputByPrefix("glibc-2.43+r22+g8362e8ce10b2-2-aarch64"), "-C", glibcArmExtract]);
disableArm64SetRobustList(join(glibcArmExtract, "usr/lib/ld-linux-aarch64.so.1"), 1);
disableArm64SetRobustList(join(glibcArmExtract, "usr/lib/libc.so.6"), 2);
disableArm64Clone3(join(glibcArmExtract, "usr/lib/libc.so.6"), 1);
disableArm64Faccessat2(join(glibcArmExtract, "usr/lib/libc.so.6"), 1);
run("tar", ["--zstd", "-xf", inputByPrefix("libstdc++-"), "-C", libstdcppExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("libgcc-"), "-C", libgccExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("ca-certificates-mozilla-"), "-C", certificatesExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("sdl2-"), "-C", sdlExtract]);
for (const [prefix, directory] of guestX11Extracts) run("tar", ["--zstd", "-xf", inputByPrefix(prefix), "-C", directory]);
run("tar", ["-xf", inputByPrefix("libxss-"), "-C", libxssExtract]);
run("tar", ["-xf", inputByPrefix("libxkbcommon-1.13.2-1-aarch64"), "-C", libxkbcommonHostExtract]);
run("tar", ["-xf", inputByPrefix("dbus-"), "-C", dbusExtract]);
run("tar", ["-xf", inputByPrefix("systemd-libs-261-1-aarch64"), "-C", systemdExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("systemd-libs-261-1-x86_64"), "-C", systemdGuestExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("util-linux-libs-"), "-C", utilLinuxGuestExtract]);
run("tar", ["-xf", inputByPrefix("vulkan-icd-loader-1.4.350.1-1-aarch64"), "-C", vulkanArmExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("vulkan-icd-loader-1.4.350.1-1-x86_64"), "-C", vulkanGuestExtract]);
run("tar", ["-xf", inputByPrefix("libstdc++-16.1.1+r12+g301eb08fa2c5-1-aarch64"), "-C", armStdcppExtract]);
run("tar", ["-xf", inputByPrefix("zlib-"), "-C", armZlibExtract]);
run("tar", ["-xf", inputByPrefix("libdrm-"), "-C", armDrmExtract]);
run("tar", [
  "--zstd", "-xf", resolve(projectRoot, "runtime/sources/winlator-app/app/src/main/assets/rootfs.tzst"),
  "-C", hostExtract, "--strip-components=3",
  "./usr/lib/ld-linux-aarch64.so.1",
  "./usr/lib/libc.so",
  "./usr/lib/libc.so.6",
  "./usr/lib/libdl.so.2",
  "./usr/lib/libgcc_s.so.1",
  "./usr/lib/libm.so.6",
  "./usr/lib/libpthread.so.0",
  "./usr/lib/libresolv.so.2",
  "./usr/lib/libX11.so",
  "./usr/lib/libX11.so.6",
  "./usr/lib/libX11.so.6.4.0",
  "./usr/lib/libX11-xcb.so",
  "./usr/lib/libX11-xcb.so.1",
  "./usr/lib/libX11-xcb.so.1.0.0",
  "./usr/lib/libXcursor.so",
  "./usr/lib/libXcursor.so.1",
  "./usr/lib/libXcursor.so.1.0.2",
  "./usr/lib/libXext.so",
  "./usr/lib/libXext.so.6",
  "./usr/lib/libXext.so.6.4.0",
  "./usr/lib/libXfixes.so",
  "./usr/lib/libXfixes.so.3",
  "./usr/lib/libXfixes.so.3.1.0",
  "./usr/lib/libXi.so",
  "./usr/lib/libXi.so.6",
  "./usr/lib/libXi.so.6.1.0",
  "./usr/lib/libXrandr.so",
  "./usr/lib/libXrandr.so.2",
  "./usr/lib/libXrandr.so.2.2.0",
  "./usr/lib/libXrender.so",
  "./usr/lib/libXrender.so.1",
  "./usr/lib/libXrender.so.1.3.0",
  "./usr/lib/libXau.so",
  "./usr/lib/libXau.so.6",
  "./usr/lib/libXau.so.6.0.0",
  "./usr/lib/libXdmcp.so",
  "./usr/lib/libXdmcp.so.6",
  "./usr/lib/libXdmcp.so.6.0.0",
  "./usr/lib/libxcb.so",
  "./usr/lib/libxcb.so.1",
  "./usr/lib/libxcb.so.1.1.0",
  "./usr/lib/libxcb-dri3.so",
  "./usr/lib/libxcb-dri3.so.0",
  "./usr/lib/libxcb-dri3.so.0.1.0",
  "./usr/lib/libxcb-present.so",
  "./usr/lib/libxcb-present.so.0",
  "./usr/lib/libxcb-present.so.0.0.0",
  "./usr/lib/libxcb-randr.so",
  "./usr/lib/libxcb-randr.so.0",
  "./usr/lib/libxcb-randr.so.0.1.0",
  "./usr/lib/libxcb-render.so",
  "./usr/lib/libxcb-render.so.0",
  "./usr/lib/libxcb-render.so.0.0.0",
  "./usr/lib/libxcb-shm.so",
  "./usr/lib/libxcb-shm.so.0",
  "./usr/lib/libxcb-shm.so.0.0.0",
  "./usr/lib/libxcb-sync.so",
  "./usr/lib/libxcb-sync.so.1",
  "./usr/lib/libxcb-sync.so.1.0.0",
]);
const probe = join(rootfs, "bin/hello");
mkdirSync(dirname(probe), { recursive: true });
run("x86_64-linux-gnu-gcc", ["-O2", "-s", "-fno-ident", "-Wl,--build-id=none", resolve(projectRoot, "runtime/probes/hello.c"), "-o", probe]);
copy(fexcoreSmokeBinary, join(rootfs, "bin/fexcore-smoke"), 0o755);
const sdlProbe = join(rootfs, "bin/sdl-window");
run("x86_64-linux-gnu-g++", [
  "-O2", "-s", "-fno-ident", "-Wl,--build-id=none",
  `-I${join(sdlExtract, "usr/include/SDL2")}`,
  resolve(projectRoot, "runtime/probes/sdl-window.cpp"),
  `-L${join(sdlExtract, "usr/lib")}`, "-lSDL2", "-o", sdlProbe,
]);
const audioProbe = join(rootfs, "bin/audio-tone");
run("x86_64-linux-gnu-gcc", [
  "-O2", "-s", "-fno-ident", "-Wl,--build-id=none",
  resolve(projectRoot, "runtime/probes/audio-tone.c"),
  "-lm", "-o", audioProbe,
]);
const vulkanProbe = join(rootfs, "bin/vulkan-info");
run("x86_64-linux-gnu-g++", [
  "-O2", "-s", "-fno-ident", "-Wl,--build-id=none",
  `-I${resolve(projectRoot, "runtime/sources/mesa/include")}`,
  resolve(projectRoot, "runtime/probes/vulkan-info.cpp"),
  `-L${join(vulkanGuestExtract, "usr/lib")}`,
  "-lvulkan", "-o", vulkanProbe,
]);
copy(join(glibcExtract, "usr/lib/ld-linux-x86-64.so.2"), join(rootfs, "lib64/ld-linux-x86-64.so.2"), 0o755);
for (const library of ["libc.so.6", "libdl.so.2", "libm.so.6", "libpthread.so.0"]) {
  copy(join(glibcExtract, `usr/lib/${library}`), join(rootfs, `lib/x86_64-linux-gnu/${library}`), 0o755);
}
copy(join(libstdcppExtract, "usr/lib/libstdc++.so.6.0.35"), join(rootfs, "lib/x86_64-linux-gnu/libstdc++.so.6"), 0o755);
copy(join(libgccExtract, "usr/lib/libgcc_s.so.1"), join(rootfs, "lib/x86_64-linux-gnu/libgcc_s.so.1"), 0o755);
copy(join(systemdGuestExtract, "usr/lib/libudev.so.1.7.14"), join(rootfs, "lib/x86_64-linux-gnu/libudev.so.1"), 0o755);
copy(join(utilLinuxGuestExtract, "usr/lib/libuuid.so.1.3.0"), join(rootfs, "lib/x86_64-linux-gnu/libuuid.so.1"), 0o755);
copy(join(sdlExtract, "usr/lib/libSDL2-2.0.so.0.3000.11"), join(rootfs, "lib/x86_64-linux-gnu/libSDL2-2.0.so.0"), 0o755);
for (const library of [
  "libX11.so.6",
  "libX11-xcb.so.1",
  "libXcursor.so.1",
  "libXext.so.6",
  "libXfixes.so.3",
  "libXi.so.6",
  "libXrandr.so.2",
  "libXrender.so.1",
  "libXss.so.1",
  "libxcb.so.1",
  "libXau.so.6",
  "libXdmcp.so.6",
  "libxkbcommon.so.0",
]) {
  const source = [...guestX11Extracts.values()].map((directory) => join(directory, `usr/lib/${library}`)).find((candidate) => {
    try {
      return statSync(candidate).isFile();
    } catch {
      return false;
    }
  });
  if (!source) fail(`Missing guest X11 library: ${library}`);
  copy(source, join(rootfs, `lib/x86_64-linux-gnu/${library}`), 0o755);
}
copy(join(glibcArmExtract, "usr/lib/ld-linux-aarch64.so.1"), join(rootfs, "host/ld-linux-aarch64.so.1"), 0o755);
copy(hostBox64Binary, join(rootfs, "host/box64"), 0o755);
for (const library of ["libvulkan.so", "libvulkan.so.1", "libvulkan.so.1.4.350"]) {
  copy(join(vulkanArmExtract, `usr/lib/${library}`), join(rootfs, `host/${library}`), 0o755);
  copy(join(vulkanGuestExtract, `usr/lib/${library}`), join(rootfs, `lib/x86_64-linux-gnu/${library}`), 0o755);
}
for (const library of ["libstdc++.so", "libstdc++.so.6", "libstdc++.so.6.0.35"]) {
  copy(join(armStdcppExtract, `usr/lib/${library}`), join(rootfs, `host/${library}`), 0o755);
}
for (const library of ["libz.so", "libz.so.1", "libz.so.1.3.2"]) {
  copy(join(armZlibExtract, `usr/lib/${library}`), join(rootfs, `host/${library}`), 0o755);
}
for (const library of ["libdrm.so", "libdrm.so.2", "libdrm.so.2.134.0"]) {
  copy(join(armDrmExtract, `usr/lib/${library}`), join(rootfs, `host/${library}`), 0o755);
}
copy(join(glibcArmExtract, "usr/lib/ld-linux-aarch64.so.1"), join(nativeOutputDir, "libbachata_host_loader.so"), 0o755);
copy(hostBox64Binary, join(nativeOutputDir, "libbachata_host_box64.so"), 0o755);
for (const library of ["libc.so", "libc.so.6", "libdl.so.2", "libm.so.6", "libpthread.so.0", "libresolv.so.2"]) {
  copy(join(glibcArmExtract, `usr/lib/${library}`), join(rootfs, `host/${library}`), 0o755);
}
copy(join(hostExtract, "libgcc_s.so.1"), join(rootfs, "host/libgcc_s.so.1"), 0o755);
for (const library of [
  "libX11.so", "libX11.so.6", "libX11.so.6.4.0",
  "libX11-xcb.so", "libX11-xcb.so.1", "libX11-xcb.so.1.0.0",
  "libXcursor.so", "libXcursor.so.1", "libXcursor.so.1.0.2",
  "libXext.so", "libXext.so.6", "libXext.so.6.4.0",
  "libXfixes.so", "libXfixes.so.3", "libXfixes.so.3.1.0",
  "libXi.so", "libXi.so.6", "libXi.so.6.1.0",
  "libXrandr.so", "libXrandr.so.2", "libXrandr.so.2.2.0",
  "libXrender.so", "libXrender.so.1", "libXrender.so.1.3.0",
  "libXau.so", "libXau.so.6", "libXau.so.6.0.0",
  "libXdmcp.so", "libXdmcp.so.6", "libXdmcp.so.6.0.0",
  "libxcb.so", "libxcb.so.1", "libxcb.so.1.1.0",
  "libxcb-dri3.so", "libxcb-dri3.so.0", "libxcb-dri3.so.0.1.0",
  "libxcb-present.so", "libxcb-present.so.0", "libxcb-present.so.0.0.0",
  "libxcb-randr.so", "libxcb-randr.so.0", "libxcb-randr.so.0.1.0",
  "libxcb-render.so", "libxcb-render.so.0", "libxcb-render.so.0.0.0",
  "libxcb-shm.so", "libxcb-shm.so.0", "libxcb-shm.so.0.0.0",
  "libxcb-sync.so", "libxcb-sync.so.1", "libxcb-sync.so.1.0.0",
]) {
  copy(join(hostExtract, library), join(rootfs, `host/${library}`), 0o755);
}
for (const library of ["libxcb.so", "libxcb.so.1", "libxcb.so.1.1.0"]) {
  patchFixedString(
    join(rootfs, `host/${library}`),
    "/data/data/com.winlator/files/rootfs/tmp/.X11-unix/X",
    "/data/data/com.bachatas4.android/files/x/X",
  );
}
copy(join(libxssExtract, "usr/lib/libXss.so.1.0.0"), join(rootfs, "host/libXss.so.1"), 0o755);
copy(join(libxssExtract, "usr/lib/libXss.so.1.0.0"), join(rootfs, "host/libXss.so"), 0o755);
copy(join(libxssExtract, "usr/lib/libXss.so.1.0.0"), join(nativeOutputDir, "libXss.so"), 0o755);
copy(join(libxssExtract, "usr/lib/libXss.so.1.0.0"), join(nativeOutputDir, "libXss.so.1"), 0o755);
copy(join(libxkbcommonHostExtract, "usr/lib/libxkbcommon.so.0.13.2"), join(rootfs, "host/libxkbcommon.so.0"), 0o755);
copy(join(libxkbcommonHostExtract, "usr/lib/libxkbcommon.so.0.13.2"), join(rootfs, "host/libxkbcommon.so"), 0o755);
copy(join(libxkbcommonHostExtract, "usr/lib/libxkbcommon.so.0.13.2"), join(nativeOutputDir, "libxkbcommon.so"), 0o755);
copy(join(libxkbcommonHostExtract, "usr/lib/libxkbcommon.so.0.13.2"), join(nativeOutputDir, "libxkbcommon.so.0"), 0o755);
copy(join(dbusExtract, "usr/lib/libdbus-1.so.3.38.3"), join(rootfs, "host/libdbus-1.so.3"), 0o755);
copy(join(systemdExtract, "usr/lib/libsystemd.so.0.44.0"), join(rootfs, "host/libsystemd.so.0"), 0o755);
copy(inputByPrefix("cacert-"), join(rootfs, "etc/ssl/certs/ca-certificates.crt"), 0o644);
copy(join(certificatesExtract, "usr/share/ca-certificates/trust-source/mozilla.trust.p11-kit"), join(rootfs, "usr/share/ca-certificates/trust-source/mozilla.trust.p11-kit"), 0o644);
const patchProvenance = inputLock.winlatorGlibcPatches.map(({ path, sha256: digest }) => `${digest}  ${path}`).join("\n") + "\n";
const patchProvenancePath = join(rootfs, "usr/share/bachata/winlator-glibc-patches.sha256");
mkdirSync(dirname(patchProvenancePath), { recursive: true });
writeFileSync(patchProvenancePath, patchProvenance, { mode: 0o644 });

const shadps4Binary = join(shadps4StageDir, "bin/shadps4");
const shadps4NeededPath = join(shadps4StageDir, "needed.txt");
copy(shadps4Binary, join(rootfs, "bin/shadps4"), 0o755);
copy(shadps4NeededPath, join(rootfs, "usr/share/bachata/shadps4-needed.txt"), 0o644);
for (const library of readFileSync(shadps4NeededPath, "utf8").trim().split("\n")) {
  const runtimeLibrary = library === "ld-linux-x86-64.so.2"
    ? join(rootfs, "lib64", library)
    : join(rootfs, "lib/x86_64-linux-gnu", library);
  try {
    if (!statSync(runtimeLibrary).isFile()) fail(`Invalid shadPS4 runtime library: ${library}`);
  } catch {
    fail(`Missing shadPS4 runtime library: ${library}`);
  }
}

const files = collectFiles(rootfs).sort((left, right) => left.path < right.path ? -1 : left.path > right.path ? 1 : 0);
if (files.length === 0) fail("Runtime rootfs is empty");
if (new Set(files.map((file) => file.path)).size !== files.length) fail("Duplicate runtime paths");

const runtimeIdentity = createHash("sha256");
for (const file of files) {
  runtimeIdentity.update(file.path);
  runtimeIdentity.update("\0");
  runtimeIdentity.update(sha256(file.bytes));
  runtimeIdentity.update("\n");
}
const runtimeVersion = `box64-${revisions.box64.slice(0, 12)}-${runtimeIdentity.digest("hex").slice(0, 12)}`;
const zip = makeZip(files);
const manifest = {
  schemaVersion: 1,
  runtimeVersion,
  protocolVersion: 1,
  components: componentLock.components.map(({ name, revision }) => ({ name, revision })),
  inputs: inputLock.inputs.map(({ name, sha256: digest }) => ({ name, sha256: digest })),
  winlatorRevision: inputLock.winlatorRevision,
  winlatorGlibcPatches: inputLock.winlatorGlibcPatches,
  files: files.map(({ path, bytes }) => ({ path, size: bytes.length, sha256: sha256(bytes) })),
};
mkdirSync(outputDir, { recursive: true });
const zipPath = join(outputDir, "runtime.zip");
const manifestPath = join(outputDir, "manifest.json");
writeFileSync(`${zipPath}.tmp`, zip);
writeFileSync(`${manifestPath}.tmp`, `${JSON.stringify(manifest, null, 2)}\n`);
renameSync(`${zipPath}.tmp`, zipPath);
renameSync(`${manifestPath}.tmp`, manifestPath);
console.log(`${basename(zipPath)} sha256=${sha256(zip)} files=${files.length}`);
