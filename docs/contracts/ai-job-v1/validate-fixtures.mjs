import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// Example consistency checks, not a production JSON Schema validator.
const root = path.dirname(fileURLToPath(import.meta.url));
const bytes = (file) => readFileSync(path.join(root, file));
const read = (file) => JSON.parse(bytes(file).toString('utf8'));
const sha256 = (data) => createHash('sha256').update(data).digest('hex');
const job = read('job.json');
const success = read('success.json');
const failure = read('failure.json');
const manifest = read('artifacts/input-manifest.json');
const source = read('artifacts/mock-source.json');
const rubric = read('artifacts/rubric.json');
const output = read('artifacts/success-output.json');
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const hash = /^[0-9a-f]{64}$/;
const identityFields = ['jobId', 'submissionId', 'inputRevision', 'commitSha',
  'processingVersion', 'modelConfigVersion', 'promptVersion', 'rubricVersion'];

for (const message of [job, success, failure]) {
  assert.equal(message.schemaVersion, 'fcaj.ai.message.v1');
  assert.match(message.messageId, uuid);
  assert.match(message.jobId, uuid);
  assert.match(message.commitSha, /^[0-9a-f]{40}$/);
  assert.match(message.inputRevision, hash);
  assert.ok(Number.isInteger(message.submissionId) && message.submissionId > 0);
  assert.ok(message.occurredAt.endsWith('Z') && Number.isFinite(Date.parse(message.occurredAt)));
  for (const field of identityFields) assert.equal(message[field], job[field], field);
}
assert.equal(new Set([job.messageId, success.messageId, failure.messageId]).size, 3);
assert.equal(job.eventType, 'AI_JOB_REQUESTED');
assert.equal(job.mode, 'MOCK');
assert.equal(job.deadlineAt, null);
assert.equal(success.eventType, 'AI_JOB_COMPLETED');
assert.equal(failure.eventType, 'AI_JOB_COMPLETED');
assert.equal(success.status, 'SUCCEEDED');
assert.equal(failure.status, 'FAILED');
assert.ok(!('error' in success) && !('result' in failure));
assert.equal(failure.error.code, 'MOCK_PROCESSING_FAILED');

function verifyReference(ref, localFile, expectedKey) {
  const data = bytes(localFile);
  assert.equal(ref.key, expectedKey);
  assert.equal(ref.sha256, sha256(data), localFile + ' hash');
  assert.equal(ref.sizeBytes, data.length, localFile + ' size');
  assert.equal(ref.contentType, 'application/json');
  assert.equal(ref.versionId, null);
  assert.ok(!ref.key.includes('..') && !ref.key.startsWith('/'));
}
verifyReference(job.input.source, 'artifacts/mock-source.json', `inputs/${job.jobId}/mock-source.json`);
verifyReference(job.input.rubric, 'artifacts/rubric.json', `rubrics/${job.jobId}/rubric.json`);
verifyReference(job.input.manifest, 'artifacts/input-manifest.json', `inputs/${job.jobId}/input-manifest.json`);
verifyReference(success.result, 'artifacts/success-output.json', `results/${job.jobId}/success-output.json`);
assert.equal(job.inputRevision, job.input.manifest.sha256);
assert.deepEqual(manifest.source, job.input.source);
assert.deepEqual(manifest.rubric, job.input.rubric);
assert.equal(manifest.commitSha, job.commitSha);
assert.equal(manifest.submissionId, job.submissionId);
assert.equal(manifest.fixtureOnly, true);
assert.equal(source.fixtureOnly, true);
assert.equal(source.commitSha, job.commitSha);
for (const field of ['processingVersion', 'modelConfigVersion', 'promptVersion', 'rubricVersion']) {
  assert.equal(manifest[field], job[field], 'manifest ' + field);
}
assert.equal(rubric.rubricVersion, job.rubricVersion);
for (const field of identityFields) assert.equal(output[field], job[field], 'output ' + field);
assert.equal(output.schemaVersion, 'fcaj.ai.output.v1');
assert.equal(output.provenance.mode, 'MOCK');
assert.equal(output.provenance.modelId, 'mock-worker');
assert.equal(output.insight.repo.analyzed, false);
assert.equal(output.insight.criteriaInsights.length, rubric.criteria.length);
for (const insight of output.insight.criteriaInsights) {
  const criterion = rubric.criteria.find((item) => item.criteriaId === insight.criteriaId);
  assert.ok(criterion, 'criterion exists');
  assert.equal(insight.criteriaName, criterion.name);
  assert.equal(insight.suggestedScoreRange, null, 'mock must not fabricate a score');
  for (const evidence of insight.evidence) {
    const file = source.files.find((item) => item.path === evidence.path);
    assert.ok(file, 'evidence path exists');
    const lineCount = file.content.replace(/\n$/, '').split('\n').length;
    assert.ok(Number.isInteger(evidence.startLine) && evidence.startLine >= 1);
    assert.ok(Number.isInteger(evidence.endLine) && evidence.endLine >= evidence.startLine);
    assert.ok(evidence.endLine <= lineCount, 'evidence lines exist');
  }
}
console.log('PASS: 7 JSON fixtures; identities, hashes/sizes, revision, keys and evidence agree. No network/AWS calls.');
