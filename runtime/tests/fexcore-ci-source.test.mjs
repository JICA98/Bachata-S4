import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const workflowUrl = new URL("../../.github/workflows/build.yml", import.meta.url);

test("Android CI runs every FEXCore source regression test", () => {
  const workflow = readFileSync(workflowUrl, "utf8");

  assert.match(
    workflow,
    /- name: Run FEXCore source regression tests\n\s+run: node --test runtime\/tests\/fexcore-\*-source\.test\.mjs/,
  );
});
