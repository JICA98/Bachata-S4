#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import {
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const EXPECTED_TOTAL = 426;
const EXPECTED_RUNNABLE = 382;

export function validateTestCatalog(testNames, evidence) {
  if (testNames.length !== EXPECTED_TOTAL) {
    throw new Error(`CTest catalog must contain exactly ${EXPECTED_TOTAL} tests; found ${testNames.length}`);
  }

  const uniqueNames = new Set(testNames);
  if (uniqueNames.size !== testNames.length) {
    throw new Error("CTest catalog contains a duplicate test name");
  }

  for (const failingTest of evidence.failingTests) {
    if (!uniqueNames.has(failingTest)) {
      throw new Error(`CTest catalog is missing allowlisted test: ${failingTest}`);
    }
  }

  const excludedTests = [...evidence.failingTests];
  const excludedSet = new Set(excludedTests);
  const runnableTests = testNames.filter((name) => !excludedSet.has(name));
  if (runnableTests.length !== EXPECTED_RUNNABLE) {
    throw new Error(`CTest catalog must leave exactly ${EXPECTED_RUNNABLE} runnable tests; found ${runnableTests.length}`);
  }

  return { excludedTests, runnableTests };
}

export function formatExclusionFile(excludedTests) {
  return `${excludedTests.join("\n")}\n`;
}

function runCtest(args) {
  const result = spawnSync("ctest", args, {
    encoding: "utf8",
    shell: false,
  });
  if (result.error) {
    throw result.error;
  }
  return result;
}

function resultExitStatus(result) {
  return Number.isInteger(result.status) ? result.status : 1;
}

function validatePassingSummary(output) {
  const summary = output.match(/(\d+)% tests passed,\s*(\d+) tests failed out of (\d+)/);
  if (!summary) {
    throw new Error("CTest output did not contain a test summary");
  }

  const [, passedPercent, failed, total] = summary.map(Number);
  if (passedPercent !== 100 || failed !== 0 || total !== EXPECTED_RUNNABLE) {
    throw new Error(
      `CTest summary must report ${EXPECTED_RUNNABLE} tests and zero failures; got ${failed} failures out of ${total}`,
    );
  }
}

export function main(args) {
  if (args.length !== 1) {
    console.error("Usage: run-ctest-with-baseline.mjs <configured-cmake-build-directory>");
    return 2;
  }

  const scriptDir = dirname(fileURLToPath(import.meta.url));
  const evidencePath = resolve(
    scriptDir,
    "../evidence/baseline/dbe5165b-ctest.json",
  );
  const evidence = JSON.parse(readFileSync(evidencePath, "utf8"));
  const buildDirectory = args[0];

  const catalogResult = runCtest([
    "--test-dir",
    buildDirectory,
    "--show-only=json-v1",
  ]);
  if (catalogResult.status !== 0) {
    process.stdout.write(catalogResult.stdout);
    process.stderr.write(catalogResult.stderr);
    return resultExitStatus(catalogResult);
  }

  let catalog;
  try {
    catalog = JSON.parse(catalogResult.stdout).tests.map((entry) => entry.name);
  } catch (error) {
    throw new Error(`Unable to parse CTest JSON catalog: ${error.message}`, { cause: error });
  }

  const { excludedTests } = validateTestCatalog(catalog, evidence);
  const temporaryDirectory = mkdtempSync(join(tmpdir(), "shadps4-ctest-baseline-"));
  const exclusionPath = join(temporaryDirectory, "excluded-tests.txt");

  try {
    writeFileSync(exclusionPath, formatExclusionFile(excludedTests), {
      encoding: "utf8",
      mode: 0o600,
    });
    const testResult = runCtest([
      "--test-dir",
      buildDirectory,
      "--exclude-from-file",
      exclusionPath,
      "--output-on-failure",
    ]);
    process.stdout.write(testResult.stdout);
    process.stderr.write(testResult.stderr);
    if (testResult.status !== 0) {
      return resultExitStatus(testResult);
    }
    validatePassingSummary(`${testResult.stdout}\n${testResult.stderr}`);
    return 0;
  } finally {
    rmSync(temporaryDirectory, { recursive: true, force: true });
  }
}

const invokedAsScript = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;

if (invokedAsScript) {
  try {
    process.exitCode = main(process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
