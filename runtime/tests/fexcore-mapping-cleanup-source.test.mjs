import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sourceUrl = new URL("../probes/fexcore-smoke.cpp", import.meta.url);

test("FEXCore smoke reports a mapping cleanup failure", () => {
  const source = readFileSync(sourceUrl, "utf8");

  assert.match(
    source,
    /class Mapping final \{[\s\S]*?~Mapping\(\) \{\s*if \(Address != MAP_FAILED && munmap\(Address, Size\) != 0\) \{\s*std::fprintf\(stderr, "FEXCORE_SMOKE_FATAL check=release-mapping errno=%d\\n", errno\);\s*std::abort\(\);\s*\}\s*\}[\s\S]*?\n\};/,
  );
});
