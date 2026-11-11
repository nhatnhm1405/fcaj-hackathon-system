// Read-only extension of the JWT checkpoint for the BE2 response documents.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {existsSync, readFileSync, statSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const prefix = path.relative(root, here).replaceAll('\\', '/');
const manifestPath = `${prefix}/response-review.json`;
const read = p => readFileSync(path.join(root, p));
const json = p => JSON.parse(read(p).toString('utf8').replace(/^\uFEFF/, ''));
const hash = p => createHash('sha256').update(read(p)).digest('hex');
const manifest = json(manifestPath);
const priorPath = manifest.previousCheckpoint.path;
assert.equal(hash(priorPath), manifest.previousCheckpoint.sha256, 'Historical JWT checkpoint changed');
const prior = json(priorPath);
const beforePath = path.posix.join(path.posix.dirname(priorPath), 'before-files.json');
const expected = new Map(json(beforePath).files.map(entry => [entry.path, entry.sha256]));
for (const entry of prior.files) expected.set(entry.path, entry.sha256);
expected.set(priorPath, manifest.previousCheckpoint.sha256);
for (const [file, digest] of expected) assert.equal(hash(file), digest, `Previous source/evidence changed: ${file}`);
for (const entry of manifest.newFiles) assert.equal(hash(entry.path), entry.sha256, `Response drift: ${entry.path}`);
const actualPaths = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {cwd: root, encoding: 'utf8'})
  .split('\0').filter(p => p && existsSync(path.join(root, p)) && statSync(path.join(root, p)).isFile()).sort();
const expectedPaths = [...expected.keys(), ...manifest.newFiles.map(entry => entry.path), manifestPath].sort();
assert.deepEqual(actualPaths, expectedPaths, 'Unreviewed inventory change');
assert.equal(hash(manifest.questionSnapshot.path), manifest.questionSnapshot.sha256);
assert.equal(manifest.questionSourceSha256, manifest.questionSnapshot.sha256);
assert.equal(execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8'}).trim(), manifest.localHead);
for (const entry of manifest.reviewedRemoteFiles) {
  const blob = execFileSync('git', ['rev-parse', `${manifest.remoteMain}:${entry.path}`], {cwd: root, encoding: 'utf8'}).trim();
  assert.equal(blob, entry.gitBlob, `Remote snapshot mismatch: ${entry.path}`);
}
const results = json('docs/checks/jwt-session-regression-2026-11-03/test-results.json');
const full = results.runs.find(run => run.name === 'full-package');
assert.deepEqual(full.totals, {tests: 633, failures: 0, errors: 0, skipped: 9});
let links = 0;
for (const match of read(manifest.reportPath).toString('utf8').matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
  const target = match[1].replace(/^<|>$/g, '').split('#')[0];
  if (!target || /^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
  assert.ok(existsSync(path.resolve(root, path.dirname(manifest.reportPath), decodeURIComponent(target))), `Broken report link: ${target}`);
  links++;
}
console.log(`PASS: ${expected.size} previous files unchanged; ${manifest.newFiles.length + 1} response files only; source/remote snapshot and ${links} report links verified. No commit/push/deploy performed.`);
