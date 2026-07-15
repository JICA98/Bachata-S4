import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  formatExclusionFile,
  validateTestCatalog,
} from "./run-ctest-with-baseline.mjs";

const EXPECTED_FAILURES = [
  "GcnTest.add_f32",
  "GcnTest.add_nan",
  "GcnTest.add_f16",
  "GcnTest.add_f16_clamp",
  "GcnTest.add_f16_neg",
  "GcnTest.add_f16_opsel_hi",
  "GcnTest.sub_f16",
  "GcnTest.mul_legacy_nan",
  "GcnTest.mul_nan",
  "GcnTest.min_legacy_nan",
  "GcnTest.min_nan",
  "GcnTest.add3_u32_1",
  "GcnTest.add3_u32_2",
  "GcnTest.add3_u32_3",
  "GcnTest.add3_u32_4",
  "GcnTest.or3_u32_1",
  "GcnTest.or3_u32_2",
  "GcnTest.or3_u32_3",
  "GcnTest.or3_u32_4",
  "GcnTest.and_or_b32_1",
  "GcnTest.and_or_b32_2",
  "GcnTest.and_or_b32_3",
  "GcnTest.and_or_b32_4",
  "GcnTest.and_or_b32_5",
  "GcnTest.and_or_b32_6",
  "GcnTest.and_or_b32_7",
  "GcnTest.and_or_b32_8",
  "GcnTest.mad_mix_f32_1",
  "GcnTest.mad_mix_f32_2",
  "GcnTest.mad_mixlo_f16_1",
  "GcnTest.mad_mixhi_f16_1",
  "GcnTest.lshrrev_b16_1",
  "GcnTest.lshrrev_b16_2",
  "GcnTest.lshrrev_b16_3",
  "GcnTest.lshlrev_b16_1",
  "GcnTest.ashrrev_i16_1",
  "GcnTest.pk_add_f16_1",
  "GcnTest.pk_add_f16_2",
  "GcnTest.pk_add_f16_3",
  "GcnTest.pk_add_f16_4",
  "GcnTest.pk_add_f16_5",
  "GcnTest.pk_add_f16_neg_lo",
  "GcnTest.pk_add_f16_neg_hi",
  "GcnTest.pk_add_f16_op_sel_reversed",
];

test("locks the accepted shadPS4 CTest baseline and exact exclusions", () => {
  const evidenceUrl = new URL(
    "../evidence/baseline/dbe5165b-ctest.json",
    import.meta.url,
  );
  const evidence = JSON.parse(readFileSync(evidenceUrl, "utf8"));

  assert.deepEqual(Object.keys(evidence).sort(), [
    "ctest",
    "failingTests",
    "failurePhase",
    "failureProcess",
    "schemaVersion",
    "sourceRevision",
  ]);
  assert.equal(evidence.schemaVersion, 1);
  assert.equal(
    evidence.sourceRevision,
    "dbe5165b86dfd4f9b251be5175d59cdadb26c3b9",
  );
  assert.deepEqual(evidence.ctest, { total: 426, passed: 382, failed: 44 });
  assert.deepEqual(evidence.failureProcess, { signal: 11, exitStatus: 139 });
  assert.equal(new Set(evidence.failingTests).size, 44);
  assert.deepEqual(
    [...evidence.failingTests].sort(),
    [...EXPECTED_FAILURES].sort(),
  );

  const passingTests = Array.from(
    { length: 382 },
    (_, index) => `PassingTest.case_${index + 1}`,
  );
  const catalog = [...passingTests, ...EXPECTED_FAILURES];
  const { excludedTests, runnableTests } = validateTestCatalog(catalog, evidence);
  const exclusionFile = formatExclusionFile(excludedTests);
  const exclusionLines = exclusionFile.trimEnd().split("\n");

  assert.equal(exclusionLines.length, 44);
  assert.deepEqual(exclusionLines, EXPECTED_FAILURES);
  assert.equal(exclusionFile, `${EXPECTED_FAILURES.join("\n")}\n`);
  assert.ok(!exclusionLines.some((name) => /^\^?GcnTest(?:\.\*)?\$?$/.test(name)));
  assert.equal(runnableTests.length, 382);

  const catalogMissingAllowlistedTest = [...catalog];
  catalogMissingAllowlistedTest[catalog.indexOf(EXPECTED_FAILURES[0])] =
    "PassingTest.replacement";
  assert.throws(
    () => validateTestCatalog(catalogMissingAllowlistedTest, evidence),
    /missing allowlisted test/,
  );
  assert.throws(
    () => validateTestCatalog([...catalog.slice(0, -1), catalog[0]], evidence),
    /duplicate test name/,
  );
  assert.throws(
    () => validateTestCatalog([...catalog, "PassingTest.unexpected"], evidence),
    /catalog must contain exactly 426 tests/,
  );
});
