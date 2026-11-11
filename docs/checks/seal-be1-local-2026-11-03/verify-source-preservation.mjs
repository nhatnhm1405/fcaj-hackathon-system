// Read-only audit against the pre-rename inventory and baseline Git source.
import assert from 'node:assert/strict';
import {readFileSync, existsSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const baseline = JSON.parse(readFileSync(path.join(here, 'baseline-files.json'), 'utf8').replace(/^\uFEFF/, ''));
const revision = 'bab44e5fdeb710e6c71982b2e8f5547829fa8e40';
const renamed = p => p.replace('back-end/src/fcaj-api', 'back-end/src/seal-api').replace('/com/fcaj/', '/com/seal/');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const normalized = bytes => bytes.toString('utf8').replaceAll('\r\n', '\n');
const replaced = s => s.replaceAll('fcaj-api', 'seal-api').replaceAll('com.fcaj', 'com.seal').replaceAll('com/fcaj', 'com/seal');
const moduleFiles = baseline.filter(f => f.path.startsWith('back-end/src/fcaj-api/'));
for (const file of moduleFiles) assert.ok(existsSync(path.join(root, renamed(file.path))), `Missing original file: ${file.path}`);
const frozen = baseline.filter(f => f.path.includes('/src/main/resources/db/migration/') ||
  f.path.startsWith('docs/contracts/ai-job-v1/') && !f.path.endsWith('/README.md'));
for (const file of frozen) assert.equal(hash(readFileSync(path.join(root, renamed(file.path)))), file.sha256, `Frozen bytes changed: ${file.path}`);

const java = moduleFiles.filter(f => f.path.endsWith('.java') && f.path.includes('/src/'));
const batch = execFileSync('git', ['cat-file', '--batch'], {
  cwd: root, input: java.map(f => `${revision}:${f.path}\n`).join(''), maxBuffer: 16 * 1024 * 1024});
let offset = 0;
const changed = [];
for (const file of java) {
  const end = batch.indexOf(10, offset);
  const header = batch.subarray(offset, end).toString();
  assert.match(header, /^[a-f0-9]+ blob [0-9]+$/);
  const size = Number(header.split(' ')[2]);
  const original = batch.subarray(end + 1, end + 1 + size);
  offset = end + 1 + size + 1;
  const current = readFileSync(path.join(root, renamed(file.path)));
  if (normalized(current) !== replaced(normalized(original))) changed.push(renamed(file.path));
}
const allowed = ['back-end/src/seal-api/src/main/java/com/seal/hackathon/security/JwtAuthenticationFilter.java'];
assert.deepEqual(changed.sort(), allowed.sort(), 'Original Java changes beyond package rename require explicit review');
const result = {baselineRevision: revision, scope: 'working-tree bytes, not a release or proof of test execution',
  originalModuleFilesPresent: moduleFiles.length, originalJavaFilesCompared: java.length,
  frozenMigrationAndContractFiles: frozen.length, semanticChangesToOriginalJava: changed,
  newRehearsal: 'back-end/src/seal-api/src/test/java/com/seal/hackathon/aijob/ApiSmokeRehearsalTest.java'};
if (process.argv.includes('--write')) writeFileSync(path.join(here, 'source-preservation.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result, null, 2));
