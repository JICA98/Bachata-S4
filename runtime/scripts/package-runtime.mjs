#!/usr/bin/env node

import { createHash } from "node:crypto";
import { mkdirSync, readdirSync, readFileSync, renameSync, statSync, writeFileSync } from "node:fs";
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
const rootfs = resolve(process.argv[2] ?? resolve(projectRoot, "runtime/build/rootfs"));
const outputDir = resolve(process.argv[3] ?? resolve(projectRoot, "android/BachataS4/app/src/main/assets/runtime"));
const runtimeVersion = process.argv[4] ?? "box64-50c8b90b09b4";
const files = collectFiles(rootfs).sort((left, right) => left.path.localeCompare(right.path, "en"));
if (files.length === 0) fail("Runtime rootfs is empty");
if (new Set(files.map((file) => file.path)).size !== files.length) fail("Duplicate runtime paths");

const zip = makeZip(files);
const manifest = {
  schemaVersion: 1,
  runtimeVersion,
  protocolVersion: 1,
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
