import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sourceUrl = new URL("../probes/fexcore-smoke.cpp", import.meta.url);

test("FEXCore diagnostic runner records fatal signal fault context only when enabled", () => {
  const source = readFileSync(sourceUrl, "utf8");

  assert.match(source, /#if defined\(FEXCORE_SMOKE_DIAGNOSTIC\)[\s\S]*void FaultDiagnosticHandler\(/);
  assert.match(source, /FEXCORE_DIAG:FAULT signal=/);
  assert.match(source, /addr=0x/);
  assert.match(source, /pc=0x/);
  assert.match(source, /uc_mcontext\.pc/);
  assert.match(source, /InstallFaultDiagnosticHandler\(SIGSEGV\)/);
  assert.match(source, /InstallFaultDiagnosticHandler\(SIGILL\)/);
  assert.match(source, /InstallFaultDiagnosticHandler\(SIGBUS\)/);
  assert.match(source, /InstallFaultDiagnosticHandler\(SIGSYS\)/);
  assert.match(source, /_exit\(128 \+ signal\)/);
  assert.match(source, /#if defined\(FEXCORE_SMOKE_DIAGNOSTIC\)[\s\S]*void DumpDiagnosticExecutableMaps\(/);
  assert.match(source, /\/proc\/self\/maps/);
  assert.match(source, /FEXCORE_DIAG:MAP %s\\n/);
  assert.match(source, /FEXCORE_DUMP_EXECUTABLE_MAPS\(\)/);

  assert.match(
    source,
    /FEXCORE_DIAG\("EXECUTE_BEFORE"\);\n\s*FEXCORE_DUMP_EXECUTABLE_MAPS\(\);\n\s*if \(!FEXCORE_INSTALL_FAULT_DIAGNOSTIC\(\)\) return Fail\("install-fault-diagnostic"\);\n\s*context->ExecuteThread\(thread\);/,
  );

  const macroOff = source.slice(source.indexOf("#else", source.indexOf("FEXCORE_SMOKE_DIAGNOSTIC")));
  assert.doesNotMatch(macroOff, /FaultDiagnosticHandler|InstallFaultDiagnosticHandlers|DumpDiagnosticExecutableMaps/);
});
