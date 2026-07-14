import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const checkoutScript = join(projectRoot, "runtime", "scripts", "checkout-component.sh");
const gitPath = execFileSync("sh", ["-c", "command -v git"], { encoding: "utf8" }).trim();

function git(directory, args) {
  const result = spawnSync(gitPath, ["-C", directory, ...args], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}

test("checkout helper removes untracked files without removing ignored caches", (t) => {
  const component = `checkout-cleanliness-${process.pid}-${Date.now()}`;
  const source = join(projectRoot, "runtime", "sources", component);
  const bin = mkdtempSync(join(tmpdir(), "checkout-component-git-"));
  const fakeGit = join(bin, "git");
  const url = "https://example.invalid/checkout-cleanliness.git";

  t.after(() => {
    rmSync(source, { recursive: true, force: true });
    rmSync(bin, { recursive: true, force: true });
  });

  mkdirSync(source, { recursive: true });
  git(source, ["init"]);
  git(source, ["config", "user.email", "checkout-test@example.invalid"]);
  git(source, ["config", "user.name", "Checkout Test"]);
  writeFileSync(join(source, ".gitignore"), "ignored-cache/\n");
  writeFileSync(join(source, "tracked.txt"), "tracked\n");
  git(source, ["add", ".gitignore", "tracked.txt"]);
  git(source, ["commit", "-m", "fixture"]);
  const revision = git(source, ["rev-parse", "HEAD"]);
  git(source, ["remote", "add", "origin", url]);

  mkdirSync(join(source, "untracked-patch"));
  writeFileSync(join(source, "untracked-patch", "patch.c"), "patch\n");
  mkdirSync(join(source, "ignored-cache"));
  writeFileSync(join(source, "ignored-cache", "cache.bin"), "cache\n");
  writeFileSync(
    fakeGit,
    `#!/bin/sh\nif [ "$1" = "-C" ] && [ "$3" = "fetch" ]; then exit 0; fi\nexec ${gitPath} "$@"\n`,
    { mode: 0o755 },
  );

  const result = spawnSync("bash", [checkoutScript, component, url, revision], {
    encoding: "utf8",
    env: { ...process.env, PATH: `${bin}:${process.env.PATH}` },
  });

  assert.equal(result.status, 0, result.stderr);
  assert.equal(existsSync(join(source, "untracked-patch")), false);
  assert.equal(existsSync(join(source, "ignored-cache", "cache.bin")), true);
  assert.equal(git(source, ["status", "--porcelain"]), "");
});
