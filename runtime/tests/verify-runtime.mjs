#!/usr/bin/env node

import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const EXPECTED_COMPONENTS = [
  { name: "box64", url: "https://github.com/ptitSeb/box64.git", revision: "50c8b90b09b433ab0767de44af2d0731cb0748b7", license: "MIT" },
  { name: "winlator-app", url: "https://github.com/brunodev85/winlator-app.git", revision: "e113da42beefc39c69c8944b27c19c3703bfa856", license: "LGPL-2.1" },
  { name: "winlator-components", url: "https://github.com/brunodev85/winlator.git", revision: "fb66541b93a4eb3ee585a433b4c7b20544d58e40", license: "MIT" },
  { name: "glibc-packages", url: "https://github.com/termux-pacman/glibc-packages.git", revision: "26d89ba7a1f856b99f0d437bef54f558b2485075", license: "mixed" },
  { name: "mesa", url: "https://gitlab.freedesktop.org/mesa/mesa.git", revision: "6984e91b5fe1d1c204e54954a4282fcdc0c44b78", license: "MIT" },
];
const EXPECTED_INPUTS = [
  { name: "glibc-2.43+r22+g8362e8ce10b2-2-x86_64.pkg.tar.zst", url: "https://archive.archlinux.org/packages/g/glibc/glibc-2.43%2Br22%2Bg8362e8ce10b2-2-x86_64.pkg.tar.zst", sha256: "2c20828b3a571b272697671c90b1e3a8c426d6a7e7fb99a242099373f2710fe1" },
  { name: "libstdc++-16.1.1+r12+g301eb08fa2c5-1-x86_64.pkg.tar.zst", url: "https://archive.archlinux.org/packages/l/libstdc%2B%2B/libstdc%2B%2B-16.1.1%2Br12%2Bg301eb08fa2c5-1-x86_64.pkg.tar.zst", sha256: "5eb8ab787086682875805e7eaad8728e73bad687aba00c9460ecb261c3762aeb" },
  { name: "ca-certificates-mozilla-3.125-1-x86_64.pkg.tar.zst", url: "https://archive.archlinux.org/packages/c/ca-certificates-mozilla/ca-certificates-mozilla-3.125-1-x86_64.pkg.tar.zst", sha256: "0fa76c249c0a6c28963f02ae366730a739121585aa1ffbb09b106b7a4fc8f358" },
  { name: "cacert-2025-02-25.pem", url: "https://curl.se/ca/cacert-2025-02-25.pem", sha256: "50a6277ec69113f00c5fd45f09e8b97a4b3e32daa35d3a95ab30137a55386cef" },
  { name: "sdl2-2.30.11-1-x86_64.pkg.tar.zst", url: "https://archive.archlinux.org/packages/s/sdl2/sdl2-2.30.11-1-x86_64.pkg.tar.zst", sha256: "7f2b1abdd245c83585d2ccf69d86fc596b87c45c54d59984ab195c403d3ae41f" },
];
const EXPECTED_WINLATOR_PATCHES = [
  { path: "sysdeps/unix/sysv/linux/android_sysvshm.c", sha256: "3698e8e9cc8e00790f60ec16fbf88c88a78da933740e003677a6c37e509c71c2" },
  { path: "sysdeps/unix/sysv/linux/android_sysvshm.h", sha256: "031fa071ca44d1191dc22b4adbf351191bb292d718af0afd2caa9291aabf6550" },
  { path: "sysdeps/unix/sysv/linux/shmat.c", sha256: "e6b80913003e80ef4f900322398b682a76c1806dbd2f2da2a8f58a0759897f66" },
  { path: "sysdeps/unix/sysv/linux/shmctl.c", sha256: "fe72ee45e0c4cd6215f109c358bdbbfbeef22a6922ea0f91758226a671bc7d4b" },
  { path: "sysdeps/unix/sysv/linux/shmdt.c", sha256: "4e4362296e2e572e36e48617dce286639b3d4c2998afc7b8737a201b1e91069e" },
  { path: "sysdeps/unix/sysv/linux/shmget.c", sha256: "e59d1b5574e05cd6683202a6e0f61addea2eccca61ebad66682703a9cd477299" },
];
const REQUIRED_RUNTIME_PATHS = [
  "bin/hello",
  "bin/sdl-window",
  "etc/ssl/certs/ca-certificates.crt",
  "lib/x86_64-linux-gnu/libc.so.6",
  "lib/x86_64-linux-gnu/libdl.so.2",
  "lib/x86_64-linux-gnu/libm.so.6",
  "lib/x86_64-linux-gnu/libpthread.so.0",
  "lib/x86_64-linux-gnu/libstdc++.so.6",
  "lib/x86_64-linux-gnu/libSDL2-2.0.so.0",
  "lib64/ld-linux-x86-64.so.2",
  "usr/share/bachata/winlator-glibc-patches.sha256",
];

function fail(message) {
  throw new Error(message);
}

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function parseStoredZip(bytes) {
  const eocdSignature = 0x06054b50;
  let eocd = -1;
  for (let offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 65_557); offset--) {
    if (bytes.readUInt32LE(offset) === eocdSignature) {
      eocd = offset;
      break;
    }
  }
  if (eocd < 0) fail("ZIP end record missing");
  const entryCount = bytes.readUInt16LE(eocd + 10);
  const centralOffset = bytes.readUInt32LE(eocd + 16);
  const entries = [];
  let offset = centralOffset;
  for (let index = 0; index < entryCount; index++) {
    if (bytes.readUInt32LE(offset) !== 0x02014b50) fail("Invalid ZIP central directory");
    const method = bytes.readUInt16LE(offset + 10);
    const time = bytes.readUInt16LE(offset + 12);
    const date = bytes.readUInt16LE(offset + 14);
    const compressedSize = bytes.readUInt32LE(offset + 20);
    const size = bytes.readUInt32LE(offset + 24);
    const nameLength = bytes.readUInt16LE(offset + 28);
    const extraLength = bytes.readUInt16LE(offset + 30);
    const commentLength = bytes.readUInt16LE(offset + 32);
    const localOffset = bytes.readUInt32LE(offset + 42);
    const path = bytes.subarray(offset + 46, offset + 46 + nameLength).toString("utf8");
    if (method !== 0 || compressedSize !== size) fail(`ZIP entry must be stored: ${path}`);
    if (time !== 0 || date !== 0) fail(`ZIP timestamp is not zero: ${path}`);
    if (bytes.readUInt32LE(localOffset) !== 0x04034b50) fail(`Invalid local header: ${path}`);
    const localNameLength = bytes.readUInt16LE(localOffset + 26);
    const localExtraLength = bytes.readUInt16LE(localOffset + 28);
    const dataOffset = localOffset + 30 + localNameLength + localExtraLength;
    entries.push({ path, bytes: bytes.subarray(dataOffset, dataOffset + size) });
    offset += 46 + nameLength + extraLength + commentLength;
  }
  return entries;
}

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, "../..");
const lockPath = resolve(process.argv[2] ?? resolve(projectRoot, "runtime/locks/components.lock.json"));
const inputLockPath = resolve(projectRoot, "runtime/locks/runtime-inputs.lock.json");
const zipPath = resolve(process.argv[3] ?? resolve(projectRoot, "android/BachataS4/app/src/main/assets/runtime/runtime.zip"));
const manifestPath = resolve(process.argv[4] ?? resolve(projectRoot, "android/BachataS4/app/src/main/assets/runtime/manifest.json"));

const lock = JSON.parse(readFileSync(lockPath, "utf8"));
if (lock.schemaVersion !== 1) fail("Lock schemaVersion must be 1");
if (JSON.stringify(lock.components) !== JSON.stringify(EXPECTED_COMPONENTS)) fail("Locked components differ from approved upstreams");
for (const component of lock.components) {
  if (!/^[0-9a-f]{40}$/.test(component.revision)) fail(`Invalid revision: ${component.name}`);
  if (!/^https:\/\//.test(component.url)) fail(`Invalid URL: ${component.name}`);
  if (!component.license) fail(`Missing license: ${component.name}`);
}
const inputLock = JSON.parse(readFileSync(inputLockPath, "utf8"));
if (inputLock.schemaVersion !== 1) fail("Input lock schemaVersion must be 1");
if (JSON.stringify(inputLock.inputs) !== JSON.stringify(EXPECTED_INPUTS)) fail("Runtime inputs differ from approved artifacts");
if (inputLock.winlatorRevision !== EXPECTED_COMPONENTS[2].revision) fail("Winlator patch revision mismatch");
if (JSON.stringify(inputLock.winlatorGlibcPatches) !== JSON.stringify(EXPECTED_WINLATOR_PATCHES)) fail("Winlator patch hashes differ from approved revision");

const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
if (manifest.schemaVersion !== 1 || manifest.protocolVersion !== 1) fail("Invalid runtime manifest schema/protocol");
if (!manifest.runtimeVersion) fail("Missing runtimeVersion");
const componentProvenance = lock.components.map(({ name, revision }) => ({ name, revision }));
const inputProvenance = inputLock.inputs.map(({ name, sha256: digest }) => ({ name, sha256: digest }));
if (JSON.stringify(manifest.components) !== JSON.stringify(componentProvenance)) fail("Manifest component provenance mismatch");
if (JSON.stringify(manifest.inputs) !== JSON.stringify(inputProvenance)) fail("Manifest input provenance mismatch");
if (manifest.winlatorRevision !== inputLock.winlatorRevision) fail("Manifest Winlator revision mismatch");
if (JSON.stringify(manifest.winlatorGlibcPatches) !== JSON.stringify(inputLock.winlatorGlibcPatches)) fail("Manifest Winlator patch provenance mismatch");
const zipEntries = parseStoredZip(readFileSync(zipPath));
const paths = zipEntries.map((entry) => entry.path);
if (new Set(paths).size !== paths.length) fail("Duplicate ZIP paths");
if (JSON.stringify(paths) !== JSON.stringify([...paths].sort())) fail("ZIP paths are not lexical");
for (const required of REQUIRED_RUNTIME_PATHS) {
  if (!paths.includes(required)) fail(`Required runtime file missing: ${required}`);
}
if (!Array.isArray(manifest.files) || manifest.files.length !== zipEntries.length) fail("Manifest file count mismatch");
for (let index = 0; index < zipEntries.length; index++) {
  const entry = zipEntries[index];
  const declared = manifest.files[index];
  if (declared.path !== entry.path) fail(`Manifest order/path mismatch: ${entry.path}`);
  if (declared.size !== entry.bytes.length) fail(`Manifest size mismatch: ${entry.path}`);
  if (declared.sha256 !== sha256(entry.bytes)) fail(`Manifest SHA-256 mismatch: ${entry.path}`);
}
const patchProvenance = zipEntries.find((entry) => entry.path === "usr/share/bachata/winlator-glibc-patches.sha256").bytes.toString("utf8");
const expectedPatchProvenance = inputLock.winlatorGlibcPatches.map(({ path, sha256: digest }) => `${digest}  ${path}`).join("\n") + "\n";
if (patchProvenance !== expectedPatchProvenance) fail("Archived Winlator patch provenance mismatch");
const hello = zipEntries.find((entry) => entry.path === "bin/hello").bytes;
if (hello.length < 20 || hello[0] !== 0x7f || hello.subarray(1, 4).toString() !== "ELF") fail("Probe is not ELF");
if (hello.readUInt16LE(18) !== 62) fail("Probe is not x86_64 ELF");

console.log(`runtime verified: ${zipEntries.length} files, sha256=${sha256(readFileSync(zipPath))}`);
