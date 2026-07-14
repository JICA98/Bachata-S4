import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const scriptUrl = new URL(
  "../scripts/build-box64-host.sh",
  import.meta.url,
);

test("Box64 host build provisions its locked source before repository use", () => {
  const script = readFileSync(scriptUrl, "utf8");

  assert.match(script, /LOCK_PATH=.*components\.lock\.json/);
  assert.match(script, /component\?\.name === "box64"/);
  assert.match(script, /box64\.name !== "box64"/);
  assert.match(script, /\^https:\\\/\\\//);
  assert.match(script, /\^\[0-9a-f\]\{40\}\$/);
  assert.match(script, /BOX64_URL=/);
  assert.match(script, /BOX64_REVISION=/);
  assert.match(
    script,
    /bash "\$project_root\/runtime\/scripts\/checkout-component\.sh" box64 "\$BOX64_URL" "\$BOX64_REVISION"/,
  );
  assert.match(
    script,
    /test "\$\(git -C "\$source_dir" rev-parse HEAD\)" = "\$BOX64_REVISION"/,
  );
  assert.doesNotMatch(script, /expected_revision=/);

  const checkout = script.indexOf("checkout-component.sh");
  const firstRepositoryOperation = script.indexOf('git -C "$source_dir"');
  assert.ok(checkout >= 0);
  assert.ok(firstRepositoryOperation >= 0);
  assert.ok(checkout < firstRepositoryOperation);
});
