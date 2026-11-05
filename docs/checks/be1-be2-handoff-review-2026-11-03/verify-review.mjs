// Review checkpoint: preserve the tested source snapshot; allow only explicit documentation updates.
import assert from 'node:assert/strict';
import {existsSync, readFileSync, readdirSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const relativeHere = path.relative(root, here).replaceAll('\\', '/');
const sha256 = file => createHash('sha256').update(readFileSync(path.join(root, file))).digest('hex');
const json = file => JSON.parse(readFileSync(path.join(root, file), 'utf8').replace(/^\uFEFF/, ''));
const inputs = json(`${relativeHere}/review-inputs.json`);
const basePath = 'docs/checks/seal-be1-local-2026-11-03/working-tree-manifest.json';
assert.equal(sha256(basePath), inputs.priorWorkingTreeManifestSha256, 'Historical local manifest must remain unchanged');
assert.equal(sha256(`${relativeHere}/${inputs.sourceSnapshot}`), inputs.sourceSha256, 'BE2 source snapshot changed');
const base = json(basePath);
const updatedDocs = [
  'docs/README.md',
  'docs/plans/backend1-be2-current-handoff-2026-10-28.md',
  'docs/plans/seal-be1-be2-clarifications-2026-11-03.md',
  'docs/plans/seal-be1-tra-loi-be2-truoc-deploy-2026-11-03.md',
].sort();
const originalPaths = base.files.map(file => file.path);
const livePaths = [...new Set(execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', ...base.inventoryScopes],
  {cwd: root, encoding: 'utf8'}).split('\0').filter(file => file && file !== basePath && existsSync(path.join(root, file))))].sort();
assert.deepEqual(livePaths, originalPaths, 'Source-snapshot inventory additions/deletions need a new tested checkpoint');
for (const file of base.files) {
  if (!updatedDocs.includes(file.path)) assert.equal(sha256(file.path), file.sha256, `Tested source/config/evidence drift: ${file.path}`);
}

const plan = 'docs/plans/seal-be1-be2-consolidated-handoff-plan-2026-11-03.md';
const manifestPath = `${relativeHere}/review-manifest.json`;
const reviewPaths = [...updatedDocs, plan, ...readdirSync(here).filter(file => file !== 'review-manifest.json').map(file => `${relativeHere}/${file}`)].sort();
for (const file of reviewPaths) {
  assert.ok(!file.split('/').includes('..') && !path.isAbsolute(file), 'Repository-relative review paths only');
  assert.ok(!/\.(env|p12|jks|bak|log)$/i.test(file), 'Private files cannot enter review snapshot');
}
if (process.argv.includes('--write')) {
  writeFileSync(path.join(root, manifestPath), JSON.stringify({
    scope: 'documentation reconciliation; no new application test run or release SHA',
    baselineRevision: inputs.localBaselineRevision, historicalLocalManifestSha256: sha256(basePath),
    updatedDocs, testedSnapshotFilesPreserved: base.files.length - updatedDocs.length,
    files: reviewPaths.map(file => ({path: file, sha256: sha256(file)})),
  }, null, 2) + '\n');
}
const manifest = json(manifestPath);
assert.equal(manifest.historicalLocalManifestSha256, sha256(basePath));
assert.deepEqual(manifest.updatedDocs, updatedDocs);
assert.deepEqual(manifest.files.map(file => file.path), reviewPaths, 'Review-document inventory drift');
for (const file of manifest.files) assert.equal(sha256(file.path), file.sha256, `Review-document drift: ${file.path}`);

let linkCount = 0;
for (const file of [plan, `${relativeHere}/README.md`, ...updatedDocs]) {
  for (const match of readFileSync(path.join(root, file), 'utf8').matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1].replace(/^<|>$/g, '').split('#')[0];
    if (!target || /^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
    linkCount++;
    assert.ok(existsSync(path.resolve(root, path.dirname(file), decodeURIComponent(target))), `Broken link: ${file} -> ${target}`);
  }
}
console.log(`PASS ${base.files.length - updatedDocs.length} original files unchanged; ${updatedDocs.length} documentation updates and ${reviewPaths.length} review hashes verified; ${linkCount} local links valid; no release or AWS acceptance asserted`);
