// Hash current files rather than HEAD/staging. This is deliberately not the historical release verifier.
import assert from 'node:assert/strict';
import {readFileSync, writeFileSync, existsSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const manifestPath = 'docs/checks/seal-be1-local-2026-11-03/working-tree-manifest.json';
const scopes = [
  'back-end/src/seal-api', '.gitattributes', '.github/workflows/deploy.yml', 'docker-compose.yml',
  'E2E testing/scenario-1/00_open_phase.bat', 'E2E testing/scenario-1/01_setup_phase.bat',
  'E2E testing/scenario-1/02_in_progress_phase.bat', 'E2E testing/scenario-2/00_s25_results_publish_awards.bat',
  'E2E testing/scenario-3/00_s3_completed_results_exports.bat',
  'README.md', 'docs/README.md', 'docs/contracts/ai-job-v1',
  'docs/plans/backend1-be2-current-handoff-2026-10-28.md',
  'docs/plans/backend1-final-pre-push-review-checklist-2026-10-28.md',
  'docs/plans/backend1-review-fixes-operator-runbook-2026-10-28.md',
  'docs/plans/backend1-be2-aws-smoke-inputs-2026-10-28.md',
  'docs/plans/backend1-ai-engineering-discussion-handoff-plan-2026-10-28.md',
  'docs/plans/backend1-be2-predeploy-execution-discussion-plan-2026-11-03.md',
  'docs/plans/seal-be1-ai-execution-plan-2026-11-03.md',
  'docs/plans/seal-be1-tra-loi-be2-truoc-deploy-2026-11-03.md',
  'docs/plans/seal-be1-runtime-config-runbook.md', 'docs/plans/seal-be1-smoke-data-runbook.md',
  'docs/plans/seal-be1-be2-clarifications-2026-11-03.md',
  'docs/report/seal-be1-local-completion-2026-11-03.md', 'docs/checks/seal-be1-local-2026-11-03',
];
const currentPaths = [...new Set(execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', ...scopes],
  {cwd: root, encoding: 'utf8'}).split('\0').filter(p => p && p !== manifestPath && existsSync(path.join(root, p))))].sort();
for (const p of currentPaths) assert.ok(!/(^|\/)(target|target-preserved|uploads|protected|node_modules)\/|\.env$|\.(p12|jks|bak|log)$/i.test(p), `Private/generated path in snapshot: ${p}`);
const sha256 = file => createHash('sha256').update(readFileSync(path.join(root, file))).digest('hex');
if (process.argv.includes('--write')) {
  const snapshot = {baselineRevision: 'bab44e5fdeb710e6c71982b2e8f5547829fa8e40',
    scope: 'working tree; not a committed release or proof of tests', inventoryScopes: scopes,
    files: currentPaths.map(p => ({path: p, sha256: sha256(p)}))};
  writeFileSync(path.join(root, manifestPath), JSON.stringify(snapshot, null, 2) + '\n');
}
const manifest = JSON.parse(readFileSync(path.join(root, manifestPath), 'utf8'));
assert.deepEqual(manifest.inventoryScopes, scopes, 'Manifest scope drift');
assert.deepEqual(manifest.files.map(f => f.path), currentPaths, 'Working-tree source additions/deletions require a new checkpoint');
for (const file of manifest.files) {
  assert.ok(!file.path.split('/').includes('..') && !path.isAbsolute(file.path), 'Repository-relative paths only');
  const actual = sha256(file.path);
  assert.equal(actual, file.sha256, `Working-tree drift: ${file.path}`);
}
console.log(`PASS ${manifest.files.length} working-tree hashes; baseline ${manifest.baselineRevision}; no release SHA asserted`);
