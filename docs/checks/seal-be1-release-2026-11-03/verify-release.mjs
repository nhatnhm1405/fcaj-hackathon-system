// Verify the published source commit using Git blob bytes, independent of checkout EOL.
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {existsSync, readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const manifest = JSON.parse(readFileSync(path.join(here, 'source-manifest.json'), 'utf8'));
const git = args => execFileSync('git', args, {cwd: root, maxBuffer: 64 * 1024 * 1024});
const source = manifest.sourceRevision;
assert.match(source, /^[0-9a-f]{40}$/);
assert.equal(git(['rev-parse', `${source}^`]).toString().trim(), manifest.sourceParent);
const tree = git(['ls-tree', '-r', '--full-tree', '-z', source]).toString().split('\0').filter(Boolean).map(line => {
  const [metadata, file] = line.split('\t');
  const [mode, type, oid] = metadata.split(' ');
  assert.equal(type, 'blob');
  return {path: file, mode, oid};
});
assert.deepEqual(tree, manifest.files.map(({path, mode, oid}) => ({path, mode, oid})), 'Published tree inventory mismatch');
const blobs = gitBatch(tree.map(entry => entry.oid));
for (let i = 0; i < tree.length; i++) {
  assert.equal(createHash('sha256').update(blobs[i]).digest('hex'), manifest.files[i].sha256, `Source blob mismatch: ${tree[i].path}`);
}
// A documentation-only follow-up commit may carry this verifier and updated report.
const codeScopes = ['back-end', 'front-end', '.github', '.gitattributes', 'docker-compose.yml', 'Caddyfile', 'E2E testing'];
git(['diff', '--exit-code', source, '--', ...codeScopes]);
const resultPath = 'docs/checks/jwt-session-regression-2026-11-03/test-results.json';
const resultIndex = tree.findIndex(entry => entry.path === resultPath);
assert.ok(resultIndex >= 0);
const evidence = JSON.parse(blobs[resultIndex].toString());
const full = evidence.runs.find(run => run.name === 'full-package');
const smoke = evidence.runs.find(run => run.name === 'component-smoke');
assert.deepEqual(full.totals, manifest.testSummary.fullPackage);
assert.deepEqual(smoke.totals, manifest.testSummary.componentSmoke);
assert.equal(full.totals.failures + full.totals.errors + smoke.totals.failures + smoke.totals.errors, 0);
let links = 0;
for (const file of ['README.md', 'docs/README.md', 'docs/plans/backend1-be2-current-handoff-2026-10-28.md',
  'docs/report/be1-tra-loi-be2-truoc-deploy-2026-11-03.md', 'docs/checks/seal-be1-release-2026-11-03/README.md']) {
  for (const match of readFileSync(path.join(root, file), 'utf8').matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1].replace(/^<|>$/g, '').split('#')[0];
    if (!target || /^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
    assert.ok(existsSync(path.resolve(root, path.dirname(file), decodeURIComponent(target))), `Broken link in ${file}: ${target}`);
    links++;
  }
}
console.log(`PASS: ${tree.length} published source blobs verified at ${source}; current code matches; test evidence and ${links} links valid. AWS/image acceptance remains separate.`);

function gitBatch(oids) {
  const output = execFileSync('git', ['cat-file', '--batch'], {cwd: root, input: oids.join('\n') + '\n', maxBuffer: 64 * 1024 * 1024});
  const blobs = []; let offset = 0;
  for (const oid of oids) {
    const end = output.indexOf(10, offset);
    const [actualOid, type, size] = output.subarray(offset, end).toString().split(' ');
    assert.equal(actualOid, oid); assert.equal(type, 'blob');
    offset = end + 1;
    blobs.push(output.subarray(offset, offset + Number(size)));
    offset += Number(size) + 1;
  }
  return blobs;
}
