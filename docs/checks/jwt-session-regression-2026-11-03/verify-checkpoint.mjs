// Read-only verifier for this working-tree checkpoint, not release or AWS acceptance.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {existsSync, readFileSync, statSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const self = path.relative(root, here).replaceAll('\\', '/');
const manifestPath = `${self}/checkpoint.json`;
const read = p => readFileSync(path.join(root, p));
const hash = p => createHash('sha256').update(read(p)).digest('hex');
const json = p => JSON.parse(read(p).toString('utf8').replace(/^\uFEFF/, ''));
const baseline = json(`${self}/before-files.json`);
const checkpoint = json(manifestPath);
assert.equal(execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8'}).trim(), baseline.baselineRevision,
  'This checkpoint describes the uncommitted working tree on the recorded baseline');
const changed = checkpoint.modifiedExistingPaths;
const changedSet = new Set(changed);
assert.equal(changedSet.size, changed.length);
for (const entry of baseline.files) {
  if (!changedSet.has(entry.path)) assert.equal(hash(entry.path), entry.sha256, `Unrelated preexisting file changed: ${entry.path}`);
}
assert.ok(changed.every(p => baseline.files.some(entry => entry.path === p)));
assert.ok(changed.every(p => !p.startsWith('back-end/src/seal-api/src/main/') && !p.endsWith('pom.xml')),
  'This fix is test-only; production changes require a different checkpoint and review');
const expected = [...baseline.files.map(entry => entry.path), ...checkpoint.addedPaths].filter(p => p !== manifestPath).sort();
const actual = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {cwd: root, encoding: 'utf8'})
  .split('\0').filter(p => p && p !== manifestPath && existsSync(path.join(root, p)) && statSync(path.join(root, p)).isFile()).sort();
assert.deepEqual(actual, expected, 'Working-tree inventory changed after checkpoint');
assert.deepEqual(checkpoint.files.map(entry => entry.path), [...changed, ...checkpoint.addedPaths]
  .filter(p => p !== manifestPath).sort(), 'Checkpoint does not cover all changed/new paths');
for (const entry of checkpoint.files) assert.equal(hash(entry.path), entry.sha256, `Checkpoint drift: ${entry.path}`);

const results = json(`${self}/test-results.json`);
for (const suite of results.runs) {
  const totals = suite.suites.reduce((acc, row) => {
    for (const field of ['tests', 'failures', 'errors', 'skipped']) acc[field] += row[field];
    return acc;
  }, {tests: 0, failures: 0, errors: 0, skipped: 0});
  assert.deepEqual(totals, suite.totals, `Inconsistent suite totals: ${suite.name}`);
  assert.equal(suite.exitCode, ['helper-red', 'full-package-sandbox'].includes(suite.name) ? 1 : 0);
  if (suite.name === 'helper-red') {
    assert.equal(totals.failures, 1); assert.equal(totals.errors, 0);
  } else if (suite.name === 'full-package-sandbox') {
    assert.equal(totals.failures, 0); assert.equal(totals.errors, 1);
    assert.equal(suite.environmentFailure, 'BoundedHttpTest: loopback Permission denied');
  } else {
    assert.equal(totals.failures + totals.errors, 0);
  }
}
assert.ok(results.runs.some(run => run.name === 'full-package'));
assert.ok(results.runs.some(run => run.name === 'component-smoke'));
let links = 0;
for (const entry of checkpoint.files.filter(entry => entry.path.endsWith('.md'))) {
  for (const match of read(entry.path).toString('utf8').matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1].replace(/^<|>$/g, '').split('#')[0];
    if (!target || /^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
    assert.ok(existsSync(path.resolve(root, path.dirname(entry.path), decodeURIComponent(target))), `Broken link in ${entry.path}: ${target}`);
    links++;
  }
}
console.log(`PASS: ${baseline.files.length - changed.length} original files preserved; ${changed.length} existing edits; ${checkpoint.addedPaths.length} additions; test summaries and ${links} links verified. No production change/release/AWS acceptance.`);
