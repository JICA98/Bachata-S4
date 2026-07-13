#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, lstatSync, mkdirSync, realpathSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

function fail(message) { throw new Error(message); }
function output(command, args) {
  return execFileSync(command, args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
}

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, "../..");
const rootfs = resolve(projectRoot, "runtime/build/rootfs");
const shadps4Stage = resolve(projectRoot, "runtime/build/shadps4-stage");
const box64Stage = resolve(projectRoot, "runtime/build/box64-host-stage");

function isElf(file) {
  try {
    const fd = execFileSync("dd", ["if=" + file, "bs=4", "count=1"], { stdio: ["ignore", "pipe", "pipe"] });
    return fd[0] === 0x7f && fd[1] === 0x45 && fd[2] === 0x4c && fd[3] === 0x46;
  } catch { return false; }
}
function elfNeeded(file) {
  try {
    const raw = output("readelf", ["-d", realpathSync(file)]);
    return raw.split("\n").filter(l => l.includes("Shared library:")).map(l => l.match(/\[([^\]]+)\]/)?.[1]).filter(Boolean);
  } catch { return []; }
}
function elfInterp(file) {
  try {
    const raw = output("readelf", ["-l", realpathSync(file)]);
    const m = raw.match(/program interpreter:\s*([^\]]*)\]/);
    return m ? basename(m[1]) : null;
  } catch { return null; }
}
function findLib(soname, multiPaths) {
  for (const mp of multiPaths) {
    const p = join(mp, soname);
    try { return realpathSync(p); } catch {}
  }
  return null;
}
function pkgFor(file) {
  try { return output("dpkg-query", ["-S", realpathSync(file)]).split(":")[0].trim(); } catch { return null; }
}
function queryFmt(fmt, pkg) {
  try { return output("dpkg-query", ["-W", "-f", fmt, pkg]); } catch { return ""; }
}

const amd64Paths = ["/usr/lib/x86_64-linux-gnu", "/lib/x86_64-linux-gnu"];
const arm64Paths = ["/usr/lib/aarch64-linux-gnu", "/lib/aarch64-linux-gnu"];

function resolveClosure(roots, multiPaths) {
  const resolved = new Map();
  const queue = roots.map(f => realpathSync(f));
  const seen = new Set();
  while (queue.length) {
    const f = queue.shift();
    if (seen.has(f) || !isElf(f)) continue;
    seen.add(f);
    for (const soname of elfNeeded(f)) {
      if (soname === "linux-vdso.so.1") continue;
      if (resolved.has(soname)) continue;
      const lib = findLib(soname, multiPaths);
      if (!lib) { console.error(`WARN: unresolved ${soname} for ${f}`); continue; }
      resolved.set(soname, { path: lib, pkg: pkgFor(lib) });
      queue.push(lib);
    }
    const ip = elfInterp(f);
    if (ip && !resolved.has(ip)) {
      const lib = findLib(ip, multiPaths);
      if (lib) { resolved.set(ip, { path: lib, pkg: pkgFor(lib) }); queue.push(lib); }
    }
  }
  return resolved;
}

function copyTo(source, destDir, name) {
  const target = join(destDir, name);
  copyFileSync(realpathSync(source), target);
  return target;
}
function addSym(dir, soname) {
  const unver = soname.replace(/(\.so\b)(\.\d.*)/, "$1").replace(/(\.\d+)+\.so\b/, ".so");
  if (unver === soname || unver.endsWith(".so.")) return;
  const link = join(dir, unver);
  if (!existsSync(link)) try { symlinkSync(soname, link); } catch {}
}

function addExtra(resolved, sonames, multiPaths) {
  for (const s of sonames) {
    if (!resolved.has(s)) {
      const lib = findLib(s, multiPaths);
      if (lib) resolved.set(s, { path: lib, pkg: pkgFor(lib) });
    }
  }
}

// -- MAIN --
rmSync(rootfs, { recursive: true, force: true });

const guestBin = join(rootfs, "bin");
const guestLib = join(rootfs, "lib/x86_64-linux-gnu");
const guestLib64 = join(rootfs, "lib64");
const hostDir = join(rootfs, "host");
const provDir = join(rootfs, "usr/share/bachata");
for (const d of [guestBin, guestLib, guestLib64, hostDir, provDir]) mkdirSync(d, { recursive: true });

// ---- amd64 guest ----
const shadps4Bin = join(shadps4Stage, "bin/shadps4");
const shadps4Needed = join(shadps4Stage, "needed.txt");
if (!existsSync(shadps4Bin)) fail("shadPS4 not built. Run build-shadps4-x86_64.sh first.");

const guestRoots = [shadps4Bin];
const amd64Resolved = resolveClosure(guestRoots, amd64Paths);

// Add the SDL2/Vulkan/X11 libraries that shadPS4/probes dlopen
addExtra(amd64Resolved, [
  "libSDL2-2.0.so.0", "libXss.so.1", "libxkbcommon.so.0",
  "libX11.so.6", "libXcursor.so.1", "libXext.so.6", "libXfixes.so.3",
  "libXi.so.6", "libXrandr.so.2", "libXrender.so.1", "libXau.so.6", "libXdmcp.so.6",
  "libxcb.so.1", "libX11-xcb.so.1", "libvulkan.so.1", "libudev.so.1", "libuuid.so.1",
], amd64Paths);

const provenance = {};

for (const [soname, info] of amd64Resolved) {
  const pkg = info.pkg || "unknown";
  if (soname === "ld-linux-x86-64.so.2") { copyTo(info.path, guestLib64, soname); }
  else { copyTo(info.path, guestLib, soname); addSym(guestLib, soname); }
  if (!provenance[pkg]) provenance[pkg] = { binaryPackage: pkg, sourcePackage: queryFmt("${source:Package}", pkg), version: queryFmt("${Version}", pkg), architecture: "amd64", files: [] };
  if (!provenance[pkg].files.includes(soname)) provenance[pkg].files.push(soname);
}

// ---- arm64 host ----
const box64Bin = join(box64Stage, "box64");
if (!existsSync(box64Bin)) fail("box64 not built. Run build-box64-host.sh first.");

const arm64Resolved = resolveClosure([box64Bin], arm64Paths);

addExtra(arm64Resolved, [
  "libXss.so.1", "libxkbcommon.so.0", "libdbus-1.so.3", "libsystemd.so.0",
  "libX11.so.6", "libX11-xcb.so.1", "libXcursor.so.1", "libXext.so.6",
  "libXfixes.so.3", "libXi.so.6", "libXrandr.so.2", "libXrender.so.1",
  "libxcb.so.1", "libxcb-dri3.so.0", "libxcb-present.so.0", "libxcb-randr.so.0",
  "libxcb-render.so.0", "libxcb-shm.so.0", "libxcb-sync.so.1",
  "libXau.so.6", "libXdmcp.so.6", "libvulkan.so.1", "libz.so.1", "libdrm.so.2",
  "libudev.so.1", "libuuid.so.1",
], arm64Paths);

for (const [soname, info] of arm64Resolved) {
  const pkg = info.pkg || "unknown";
  copyTo(info.path, hostDir, soname);
  addSym(hostDir, soname);
  if (!provenance[pkg]) provenance[pkg] = { binaryPackage: pkg, sourcePackage: queryFmt("${source:Package}", pkg), version: queryFmt("${Version}", pkg), architecture: "arm64", files: [] };
  if (!provenance[pkg].files.includes(soname)) provenance[pkg].files.push(soname);
}

// CA certs
const caSrc = "/etc/ssl/certs/ca-certificates.crt";
if (existsSync(caSrc)) {
  const d = join(rootfs, "etc/ssl/certs"); mkdirSync(d, { recursive: true });
  copyFileSync(caSrc, join(d, "ca-certificates.crt"));
}

// Provenance
writeFileSync(join(provDir, "debian-provenance.json"),
  JSON.stringify({ schemaVersion: 1, packages: Object.values(provenance) }, null, 2) + "\n");

// Verification
const checks = [
  ["host/ld-linux-aarch64.so.1", "arm64 loader"],
  ["host/libc.so.6", "arm64 libc"],
  ["host/libX11.so.6", "arm64 X11"],
  ["host/libvulkan.so.1", "arm64 vulkan"],
  ["host/libxkbcommon.so.0", "arm64 xkbcommon"],
  ["lib64/ld-linux-x86-64.so.2", "x86_64 loader"],
  ["lib/x86_64-linux-gnu/libc.so.6", "x86_64 libc"],
  ["lib/x86_64-linux-gnu/libSDL2-2.0.so.0", "x86_64 SDL2"],
  ["etc/ssl/certs/ca-certificates.crt", "CA certs"],
];
let missing = 0;
for (const [p, label] of checks) {
  if (!existsSync(join(rootfs, p))) { console.error(`MISSING: ${label}`); missing++; }
}
if (missing) fail(`${missing} required files missing`);

const hc = execFileSync("ls", ["-1", hostDir], { encoding: "utf8" }).split("\n").filter(Boolean).length;
const gc = execFileSync("ls", ["-1", guestLib], { encoding: "utf8" }).split("\n").filter(Boolean).length;
const gbc = existsSync(join(rootfs, "lib64/ld-linux-x86-64.so.2")) ? 1 : 0;
console.log(`Staged: host=${hc} arm64 libs, guest=${gc}+${gbc} amd64 libs, packages=${Object.keys(provenance).length}`);
