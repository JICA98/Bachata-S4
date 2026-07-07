import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("../../src/sdl_window.cpp", import.meta.url), "utf8");

test("Bachata runtime skips SDL hardware gamepad discovery", () => {
  assert.match(
    source,
    /#ifndef ENABLE_BACHATA_RUNTIME\s+SDL_InitSubSystem\(SDL_INIT_GAMEPAD\);\s+#endif/,
  );
  assert.match(
    source,
    /#ifndef ENABLE_BACHATA_RUNTIME\s+controllers\.TryOpenSDLControllers\(\);\s+#endif/,
  );
});
