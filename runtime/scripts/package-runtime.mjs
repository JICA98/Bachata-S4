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
const outputDir = resolve(process.argv[2] ?? resolve(projectRoot, "android/BachataS4/app/src/main/assets/runtime"));
const componentLock = JSON.parse(readFileSync(resolve(projectRoot, "runtime/locks/components.lock.json"), "utf8"));
const inputLock = JSON.parse(readFileSync(resolve(projectRoot, "runtime/locks/runtime-inputs.lock.json"), "utf8"));
const revisions = Object.fromEntries(componentLock.components.map(({ name, revision }) => [name, revision]));
const runtimeVersion = `box64-${revisions.box64.slice(0, 12)}`;

for (const input of inputLock.inputs) {
  const bytes = readFileSync(join(inputDir, input.name));
  if (sha256(bytes) !== input.sha256) fail(`Locked input hash mismatch: ${input.name}`);
}
for (const component of ["glibc-packages", "winlator-components"]) {
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
const libstdcppExtract = join(extractDir, "libstdcpp");
const certificatesExtract = join(extractDir, "certificates");
for (const directory of [rootfs, glibcExtract, libstdcppExtract, certificatesExtract]) mkdirSync(directory, { recursive: true });
const inputByPrefix = (prefix) => join(inputDir, inputLock.inputs.find(({ name }) => name.startsWith(prefix)).name);
run("tar", ["--zstd", "-xf", inputByPrefix("glibc-"), "-C", glibcExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("libstdc++-"), "-C", libstdcppExtract]);
run("tar", ["--zstd", "-xf", inputByPrefix("ca-certificates-mozilla-"), "-C", certificatesExtract]);
const probe = join(rootfs, "bin/hello");
mkdirSync(dirname(probe), { recursive: true });
run("x86_64-linux-gnu-gcc", ["-O2", "-s", "-fno-ident", "-Wl,--build-id=none", resolve(projectRoot, "runtime/probes/hello.c"), "-o", probe]);
copy(join(glibcExtract, "usr/lib/ld-linux-x86-64.so.2"), join(rootfs, "lib64/ld-linux-x86-64.so.2"), 0o755);
for (const library of ["libc.so.6", "libdl.so.2", "libm.so.6", "libpthread.so.0"]) {
  copy(join(glibcExtract, `usr/lib/${library}`), join(rootfs, `lib/x86_64-linux-gnu/${library}`), 0o755);
}
copy(join(libstdcppExtract, "usr/lib/libstdc++.so.6.0.35"), join(rootfs, "lib/x86_64-linux-gnu/libstdc++.so.6"), 0o755);
copy(inputByPrefix("cacert-"), join(rootfs, "etc/ssl/certs/ca-certificates.crt"), 0o644);
copy(join(certificatesExtract, "usr/share/ca-certificates/trust-source/mozilla.trust.p11-kit"), join(rootfs, "usr/share/ca-certificates/trust-source/mozilla.trust.p11-kit"), 0o644);
const patchProvenance = inputLock.winlatorGlibcPatches.map(({ path, sha256: digest }) => `${digest}  ${path}`).join("\n") + "\n";
const patchProvenancePath = join(rootfs, "usr/share/bachata/winlator-glibc-patches.sha256");
mkdirSync(dirname(patchProvenancePath), { recursive: true });
writeFileSync(patchProvenancePath, patchProvenance, { mode: 0o644 });

const files = collectFiles(rootfs).sort((left, right) => left.path.localeCompare(right.path, "en"));
if (files.length === 0) fail("Runtime rootfs is empty");
if (new Set(files.map((file) => file.path)).size !== files.length) fail("Duplicate runtime paths");

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
