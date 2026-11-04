// Compare the integration against both inputs without rewriting historical evidence.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync, existsSync} from 'node:fs';
import {createHash} from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const feature = '4049d107a78235be9e95efa9185e2b034b07c9ec';
const develop = '18d1783a9d428466c166c00c1595bc1f45b59388';
const git = args => execFileSync('git', args, {cwd: root, maxBuffer: 64 * 1024 * 1024});
const canonical = bytes => bytes.toString('utf8').replaceAll('\r\n', '\n');
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const list = (revision, scopes) => git(['ls-tree', '-r', '-z', revision, '--', ...scopes]).toString().split('\0').filter(Boolean).map(line => {
  const [metadata, file] = line.split('\t');
  const [mode, type, oid] = metadata.split(' ');
  assert.equal(type, 'blob');
  return {mode, oid, file};
});
function blobs(entries) {
  const output = execFileSync('git', ['cat-file', '--batch'], {cwd: root, input: entries.map(e => e.oid).join('\n') + '\n', maxBuffer: 64 * 1024 * 1024});
  let offset = 0;
  return entries.map(entry => {
    const end = output.indexOf(10, offset);
    const [oid, type, size] = output.subarray(offset, end).toString().split(' ');
    assert.equal(oid, entry.oid); assert.equal(type, 'blob');
    offset = end + 1;
    const bytes = output.subarray(offset, offset + Number(size));
    offset += Number(size) + 1;
    return bytes;
  });
}
assert.equal(git(['diff', '--name-only', '--diff-filter=U']).toString().trim(), '', 'Unresolved merge entries');
const backend = list(feature, ['back-end']);
const contracts = list(feature, ['docs/contracts/ai-job-v1']);
const preserved = [...backend, ...contracts];
const expectedBlobs = blobs(preserved);
const frozen = [];
preserved.forEach((entry, i) => {
  const bytes = readFileSync(path.join(root, entry.file));
  if (entry.file.includes('/resources/db/') || entry.file.startsWith('docs/contracts/')) {
    assert.equal(sha256(bytes), sha256(expectedBlobs[i]), `Frozen bytes changed: ${entry.file}`);
    frozen.push({path: entry.file, sha256: sha256(bytes)});
  } else {
    assert.equal(canonical(bytes), canonical(expectedBlobs[i]), `BE1 file changed: ${entry.file}`);
  }
});
const indexedBackend = git(['ls-files', '-z', 'back-end']).toString().split('\0').filter(Boolean).sort();
assert.deepEqual(indexedBackend, backend.map(e => e.file).sort(), 'Backend inventory changed');
assert.match(git(['ls-files', '--stage', 'back-end/src/seal-api/mvnw']).toString(), /^100755 /, 'Preserve upstream executable bit');
const coreWorkerPaths = ['ai-worker/src/ai_grader.py', 'ai-worker/src/code_cleaner.py', 'ai-worker/src/github_fetcher.py',
  'ai-worker/src/schema.py', 'ai-worker/contracts/input_schema.json', 'ai-worker/contracts/output_schema.json',
  'ai-worker/contracts/sample_input.json', 'ai-worker/requirements.txt'];
const coreWorker = list(develop, coreWorkerPaths);
const coreBlobs = blobs(coreWorker);
coreWorker.forEach((entry, i) => assert.equal(canonical(readFileSync(path.join(root, entry.file))), canonical(coreBlobs[i]), `Worker core changed: ${entry.file}`));

const e2e = 'front-end/src/seal-web/e2e/scenario-1/02_in_progress_phase.spec.ts';
assert.equal(canonical(readFileSync(path.join(root, e2e))), canonical(git(['show', `${feature}:${e2e}`])), 'Feature E2E regression was lost');
const frontend = list(develop, ['front-end/src/fcaj-web']);
const frontendBlobs = blobs(frontend);
for (let i = 0; i < frontend.length; i++) {
  const entry = frontend[i], target = entry.file.replace('front-end/src/fcaj-web/', 'front-end/src/seal-web/');
  if (target === e2e) continue;
  let expected = canonical(frontendBlobs[i]);
  if (target.endsWith('/package.json') || target.endsWith('/package-lock.json')) expected = expected.replaceAll('"name": "fcaj-web"', '"name": "seal-web"');
  const actual = readFileSync(path.join(root, target));
  if (frontendBlobs[i].includes(0)) assert.deepEqual(actual, frontendBlobs[i], `Frontend asset changed: ${target}`);
  else assert.equal(canonical(actual), expected, `Upstream frontend change lost: ${target}`);
}
const paths = git(['ls-files', '-z']).toString().split('\0').filter(Boolean);
assert.ok(!paths.some(p => p.startsWith('back-end/src/fcaj-api/') || p.startsWith('front-end/src/fcaj-web/')), 'Duplicate FCAJ modules');
const active = paths.filter(p => /\.(java|xml|properties|ps1|sh|bat|mjs|ts|tsx|css|yml|yaml)$/.test(p) &&
  (p.startsWith('back-end/') || p.startsWith('front-end/') || p.startsWith('.github/') || p.startsWith('E2E testing/')));
for (const file of [...active, 'Caddyfile', 'docker-compose.yml']) {
  if (!existsSync(path.join(root, file))) continue;
  const text = readFileSync(path.join(root, file), 'utf8');
  assert.ok(!/^(<<<<<<< |=======\r?$|>>>>>>> )/m.test(text), `Conflict marker: ${file}`);
  assert.ok(!/com\.fcaj\.hackathon|com\/fcaj\/hackathon|back-end\/src\/fcaj-api|front-end\/src\/fcaj-web|fcaj-(?:backend|frontend)(?=[:\s"'])/.test(text), `Obsolete technical identifier: ${file}`);
}
console.log(JSON.stringify({status: 'PASS', feature, develop, backendFilesPreserved: backend.length,
  contractFilesPreserved: contracts.length, frozenFiles: frozen.length, workerCoreFilesPreserved: coreWorker.length,
  frontendFilesVerified: frontend.length, executableWrapper: true}, null, 2));
