import assert from 'node:assert/strict';
import {existsSync, readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const files = [
  'README.md', 'docs/README.md',
  'docs/contracts/ai-job-v1/README.md', 'docs/contracts/ai-job-v1/CURRENT.md',
  'docs/plans/backend1-be2-current-handoff-2026-10-28.md',
  'docs/plans/backend1-final-pre-push-review-checklist-2026-10-28.md',
  'docs/plans/backend1-review-fixes-operator-runbook-2026-10-28.md',
  'docs/plans/backend1-be2-aws-smoke-inputs-2026-10-28.md',
  'docs/plans/seal-be1-ai-execution-plan-2026-11-03.md',
  'docs/plans/seal-be1-tra-loi-be2-truoc-deploy-2026-11-03.md',
  'docs/plans/seal-be1-runtime-config-runbook.md',
  'docs/plans/seal-be1-smoke-data-runbook.md',
  'docs/plans/seal-be1-be2-clarifications-2026-11-03.md',
  'docs/report/seal-be1-local-completion-2026-11-03.md',
  'docs/checks/seal-be1-local-2026-11-03/README.md',
  'back-end/src/seal-api/Test/schema/README.md',
  'back-end/src/seal-api/src/main/java/com/seal/hackathon/mockdemo/README.md',
];
const broken = []; let count = 0;
for (const file of files) {
  const content = readFileSync(path.join(root, file), 'utf8');
  for (const match of content.matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1].replace(/^<|>$/g, '').split('#')[0];
    if (!target || /^[a-z][a-z0-9+.-]*:/i.test(target)) continue;
    count++;
    if (!existsSync(path.resolve(root, path.dirname(file), decodeURIComponent(target)))) broken.push({file, target});
  }
}
assert.deepEqual(broken, [], 'Broken current-document file links');
console.log(`PASS ${count} local file links in ${files.length} current documents; historical evidence left unchanged`);
