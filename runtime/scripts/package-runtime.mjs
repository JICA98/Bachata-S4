#!/usr/bin/env node

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { chmodSync, copyFileSync, existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, realpathSync, renameSync, rmSync, statSync, symlinkSync, writeFileSync } from "node:fs";
import { basename, dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

function fail(message) { throw new Error(message); }
function sha256(bytes) { return createHash("sha256").update(bytes).digest("hex"); }
function run(command, args) { execFileSync(command, args, { stdio: "inherit" }); }

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) { crc ^= byte; for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1)); }
  return (crc ^ 0xffffffff) >>> 0;
}

function copy(source, target, mode) {
  mkdirSync(dirname(target), { recursive: true });
  copyFileSync(source, target);
  chmodSync(target, mode);
}

function isElf(file) {
  try {
    const fd = execFileSync("dd", ["if=" + file, "bs=1", "count=4"], { stdio: ["ignore", "pipe", "ignore"] });
    return fd[0] === 0x7f && fd[1] === 0x45 && fd[2] === 0x4c && fd[3] === 0x46;
  } catch { return false; }
}

// ---- glibc ARM64 compatibility patches ----

function patchFixedString(file, original, replacement) {
  const bytes = readFileSync(file);
  const source = Buffer.from(original);
  const target = Buffer.from(replacement);
  if (target.length > source.length) fail(`Replacement too long in ${file}`);
  const offset = bytes.indexOf(source);
  if (offset < 0 || bytes.indexOf(source, offset + 1) >= 0) fail(`Expected single ${original} in ${file}`);
  bytes.fill(0, offset, offset + source.length);
  target.copy(bytes, offset);
  writeFileSync(file, bytes);
}

function disableArm64SetRobustList(file, expectedCount) {
  const bytes = readFileSync(file);
  let patched = 0;
  for (let offset = 0; offset + 4 <= bytes.length; offset += 4) {
    if (bytes.readUInt32LE(offset) !== 0xd2800c68) continue;
    for (let so = offset + 4; so <= offset + 32; so += 4) {
      if (bytes.readUInt32LE(so) !== 0xd4000001) continue;
      bytes.writeUInt32LE(0xd2800000, so);
      patched++; break;
    }
  }
  console.log(`  set_robust_list: patched ${patched}, expected ~${expectedCount}`);
  writeFileSync(file, bytes);
  return patched;
}

function disableArm64Clone3(file) {
  const bytes = readFileSync(file);
  let patched = 0;
  for (let offset = 0; offset + 8 <= bytes.length; offset += 4) {
    if (bytes.readUInt32LE(offset) !== 0xd2803668 || bytes.readUInt32LE(offset + 4) !== 0xd4000001) continue;
    bytes.writeUInt32LE(0x928004a0, offset + 4);
    patched++;
  }
  if (patched === 0) console.warn(`WARNING: clone3 pattern not found in ${file}`);
  writeFileSync(file, bytes);
  return patched;
}

function disableArm64Faccessat2(file) {
  const bytes = readFileSync(file);
  let patched = 0;
  for (let offset = 0; offset + 8 <= bytes.length; offset += 4) {
    if (bytes.readUInt32LE(offset) !== 0xd28036e8 || bytes.readUInt32LE(offset + 4) !== 0xd4000001) continue;
    bytes.writeUInt32LE(0x928004a0, offset + 4);
    patched++;
  }
  if (patched === 0) console.warn(`WARNING: faccessat2 pattern not found in ${file}`);
  writeFileSync(file, bytes);
  return patched;
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
    }
  }
  return files;
}

function localHeader(entry) {
  const name = Buffer.from(entry.path, "utf8");
  const header = Buffer.alloc(30);
  header.writeUInt32LE(0x04034b50, 0);
  header.writeUInt16LE(20, 4); header.writeUInt16LE(0x0800, 6);
  header.writeUInt16LE(0, 8); header.writeUInt16LE(0, 10); header.writeUInt16LE(0, 12);
  header.writeUInt32LE(entry.crc, 14);
  header.writeUInt32LE(entry.bytes.length, 18);
  header.writeUInt32LE(entry.bytes.length, 22);
  header.writeUInt16LE(name.length, 26);
  return Buffer.concat([header, name, entry.bytes]);
}

function centralHeader(entry) {
  const name = Buffer.from(entry.path, "utf8");
  const header = Buffer.alloc(46);
  header.writeUInt32LE(0x02014b50, 0); header.writeUInt16LE(0x0314, 4);
  header.writeUInt16LE(20, 6); header.writeUInt16LE(0x0800, 8);
  header.writeUInt16LE(0, 10); header.writeUInt16LE(0, 12); header.writeUInt16LE(0, 14);
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
    if (bytes.length > 0xffffffff) fail(`ZIP64 not supported: ${path}`);
    const entry = { path, bytes, crc: crc32(bytes), offset };
    offset += 30 + Buffer.byteLength(path) + bytes.length;
    return entry;
  });
  return Buffer.concat([...entries.map(localHeader), ...entries.map(centralHeader), (() => {
    const cSize = entries.reduce((s, e) => s + (46 + Buffer.byteLength(e.path)), 0);
    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(entries.length, 8);
    eocd.writeUInt16LE(entries.length, 10); eocd.writeUInt32LE(cSize, 12);
    eocd.writeUInt32LE(offset, 16);
    return eocd;
  })()]);
}

// ---- MAIN ----

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, "../..");

const rootfs = resolve(projectRoot, "runtime/build/rootfs");
const stagedRoot = resolve(projectRoot, "runtime/build/staged");
const shadps4Stage = resolve(projectRoot, "runtime/build/shadps4-stage");
const shadps4Arm64Stage = resolve(projectRoot, "runtime/build/shadps4-arm64-stage");
const hostBox64Binary = resolve(projectRoot, "runtime/build/box64-host-stage/box64");
const hostFexcoreSmoke = join(rootfs, "host/fexcore-smoke");
const outputDir = resolve(process.argv[2] ?? resolve(projectRoot, "android/BachataS4/app/src/main/assets/runtime"));
const nativeOutputDir = resolve(projectRoot, "android/BachataS4/core/runtime/src/main/jniLibs/arm64-v8a");

const componentLock = JSON.parse(readFileSync(resolve(projectRoot, "runtime/locks/components.lock.json"), "utf8"));
const revisions = Object.fromEntries(componentLock.components.map(({ name, revision }) => [name, revision]));

if (!existsSync(join(rootfs, "host/ld-linux-aarch64.so.1"))) {
  fail(`Runtime rootfs not staged. Run stage-debian-runtime.mjs first.`);
}
if (!existsSync(hostFexcoreSmoke)) {
  fail(`FEXCore smoke not staged: ${hostFexcoreSmoke}`);
}

const hostDir = join(rootfs, "host");
const guestLibDir = join(rootfs, "lib/x86_64-linux-gnu");
const guestLib64Dir = join(rootfs, "lib64");

// Apply ARM64 glibc patches
const armLoader = join(hostDir, "ld-linux-aarch64.so.1");
const armLibc = join(hostDir, "libc.so.6");

if (!existsSync(armLoader)) fail(`ARM64 loader missing: ${armLoader}`);
if (!existsSync(armLibc)) fail(`ARM64 libc missing: ${armLibc}`);

const srPre = sha256(readFileSync(armLoader));
const lcPre = sha256(readFileSync(armLibc));
const rlCount = disableArm64SetRobustList(armLoader, 1);
const lcRlCount = disableArm64SetRobustList(armLibc, 2);
const clCount = disableArm64Clone3(armLibc);
const faCount = disableArm64Faccessat2(armLibc);

console.log(`glibc patches: loader.set_robust_list=${rlCount} libc.set_robust_list=${lcRlCount} libc.clone3=${clCount} libc.faccessat2=${faCount}`);

// Patch libxcb socket path if it contains the winlator default
const hostLibxcb = join(hostDir, "libxcb.so.1");
if (existsSync(hostLibxcb)) {
  const xcbBytes = readFileSync(hostLibxcb);
  const winlatorPath = Buffer.from("/data/data/com.winlator/files/rootfs/tmp/.X11-unix/X");
  if (xcbBytes.indexOf(winlatorPath) >= 0) {
    patchFixedString(hostLibxcb,
      "/data/data/com.winlator/files/rootfs/tmp/.X11-unix/X",
      "/data/data/com.bachatas4.android/files/x/X");
    console.log("libxcb socket path patched (winlator -> bachata)");
  } else {
    console.log("libxcb socket path: skipping patch (no winlator default found)");
  }
}

// Build probes
const probeDir = join(rootfs, "bin");
mkdirSync(probeDir, { recursive: true });

const helloProbe = join(probeDir, "hello");
run("x86_64-linux-gnu-gcc", ["-O2", "-s", "-fno-ident", "-Wl,--build-id=none", resolve(projectRoot, "runtime/probes/hello.c"), "-o", helloProbe]);

const audioProbe = join(probeDir, "audio-tone");
run("x86_64-linux-gnu-gcc", ["-O2", "-s", "-fno-ident", "-Wl,--build-id=none", resolve(projectRoot, "runtime/probes/audio-tone.c"), "-lm", "-o", audioProbe]);

// SDL and Vulkan probes - need dev packages installed
const sdlHeaders = "/usr/include/SDL2";
const sdlLibs = "/usr/lib/x86_64-linux-gnu";
const vulkanHeaders = resolve(projectRoot, "runtime/sources/mesa/include");
const vulkanLibs = "/usr/lib/x86_64-linux-gnu";

if (existsSync(sdlHeaders)) {
  const sdlProbe = join(probeDir, "sdl-window");
  run("x86_64-linux-gnu-g++", ["-O2", "-s", "-fno-ident", "-Wl,--build-id=none",
    `-I${sdlHeaders}`, resolve(projectRoot, "runtime/probes/sdl-window.cpp"),
    `-L${sdlLibs}`, "-lSDL2", "-o", sdlProbe]);
}

if (existsSync(`${vulkanLibs}/libvulkan.so`) || existsSync("/usr/lib/x86_64-linux-gnu/libvulkan.so")) {
  const vkProbe = join(probeDir, "vulkan-info");
  run("x86_64-linux-gnu-g++", ["-O2", "-s", "-fno-ident", "-Wl,--build-id=none",
    `-I${vulkanHeaders}`, resolve(projectRoot, "runtime/probes/vulkan-info.cpp"),
    `-L${vulkanLibs}`, "-lvulkan", "-o", vkProbe]);
}

// Copy source-built binaries
const shadps4Binary = join(shadps4Stage, "bin/shadps4");
const shadps4Needed = join(shadps4Stage, "needed.txt");
const shadps4Arm64Binary = join(shadps4Arm64Stage, "bin/shadps4-arm64");
const shadps4Arm64Needed = join(shadps4Arm64Stage, "needed.txt");
if (!existsSync(shadps4Binary)) fail(`shadPS4 not built: ${shadps4Binary}`);
copy(shadps4Binary, join(rootfs, "bin/shadps4"), 0o755);

const provDir = join(rootfs, "usr/share/bachata");
mkdirSync(provDir, { recursive: true });
if (existsSync(shadps4Needed)) {
  copyFileSync(shadps4Needed, join(provDir, "shadps4-needed.txt"));
  const neededLibs = readFileSync(shadps4Needed, "utf8").trim().split("\n");
  for (const lib of neededLibs) {
    const expected = lib === "ld-linux-x86-64.so.2"
      ? join(guestLib64Dir, lib)
      : join(guestLibDir, lib);
    if (!existsSync(expected)) fail(`Missing shadPS4 dep: ${lib}`);
  }
}

if (!existsSync(shadps4Arm64Binary)) {
  fail(`ARM64 FEX shadPS4 not built: ${shadps4Arm64Binary}`);
}
copy(shadps4Arm64Binary, join(hostDir, "shadps4-arm64-fex"), 0o755);
if (!existsSync(shadps4Arm64Needed)) {
  fail(`ARM64 FEX shadPS4 dependency list missing: ${shadps4Arm64Needed}`);
}
copyFileSync(shadps4Arm64Needed, join(provDir, "shadps4-arm64-fex-needed.txt"));
for (const lib of readFileSync(shadps4Arm64Needed, "utf8").trim().split("\n")) {
  if (!existsSync(join(hostDir, lib))) fail(`Missing ARM64 FEX shadPS4 dep: ${lib}`);
}

copy(hostBox64Binary, join(hostDir, "box64"), 0o755);

// Copy host libs to jniLibs
mkdirSync(nativeOutputDir, { recursive: true });
const jniMappings = [
  ["ld-linux-aarch64.so.1", "libbachata_host_loader.so"],
  ["box64", "libbachata_host_box64.so"],
  ["libXss.so.1", "libXss.so"],
  ["libxkbcommon.so.0", "libxkbcommon.so"],
];
for (const [src, dst] of jniMappings) {
  const srcPath = join(hostDir, src);
  if (existsSync(srcPath)) {
    copyFileSync(srcPath, join(nativeOutputDir, dst));
    chmodSync(join(nativeOutputDir, dst), 0o755);
  }
}
// Also copy symlinked versions for Xss/xkbcommon
for (const pair of [["libXss.so.1", "libXss.so.1"], ["libxkbcommon.so.0", "libxkbcommon.so.0"]]) {
  const srcPath = join(hostDir, pair[0]);
  const dstPath = join(nativeOutputDir, pair[1]);
  if (existsSync(srcPath) && !existsSync(dstPath)) {
    copyFileSync(srcPath, dstPath);
    chmodSync(dstPath, 0o755);
  }
}

// Package
const patchProvenance = [
  `ld-linux-aarch64.so.1 pre=${srPre} post=${sha256(readFileSync(armLoader))}`,
  `libc.so.6 pre=${lcPre} post=${sha256(readFileSync(armLibc))}`,
].join("\n") + "\n";
writeFileSync(join(provDir, "glibc-patches.txt"), patchProvenance);

const files = collectFiles(rootfs).sort((a, b) => a.path < b.path ? -1 : a.path > b.path ? 1 : 0);
if (files.length === 0) fail("rootfs is empty");

const runtimeIdentity = createHash("sha256");
for (const f of files) { runtimeIdentity.update(f.path); runtimeIdentity.update("\0"); runtimeIdentity.update(sha256(f.bytes)); runtimeIdentity.update("\n"); }
const runtimeVersion = `box64-${revisions.box64.slice(0, 12)}-${runtimeIdentity.digest("hex").slice(0, 12)}`;

const zip = makeZip(files);
const manifest = {
  schemaVersion: 2,
  runtimeVersion,
  protocolVersion: 1,
  distribution: "debian",
  components: componentLock.components.map(({ name, revision }) => ({ name, revision })),
  files: files.map(({ path, bytes }) => ({ path, size: bytes.length, sha256: sha256(bytes) })),
};

mkdirSync(outputDir, { recursive: true });
const zipPath = join(outputDir, "runtime.zip");
const manifestPath = join(outputDir, "manifest.json");
writeFileSync(`${zipPath}.tmp`, zip);
writeFileSync(`${manifestPath}.tmp`, JSON.stringify(manifest, null, 2) + "\n");
renameSync(`${zipPath}.tmp`, zipPath);
renameSync(`${manifestPath}.tmp`, manifestPath);

console.log(`runtime.zip sha256=${sha256(zip)} files=${files.length}`);
