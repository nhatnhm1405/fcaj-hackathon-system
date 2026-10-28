import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const identity=['jobId','submissionId','inputRevision','commitSha','processingVersion','modelConfigVersion','promptVersion','rubricVersion'];
const hash=b=>createHash('sha256').update(b).digest('hex');
for(const outcome of ['success','failure']) {
  const bytes=name=>readFileSync(new URL(`./current-examples/${outcome}/${name}.json`,import.meta.url));
  const read=name=>JSON.parse(bytes(name));
  const job=read('job'),completion=read('completion'),manifest=read('manifest'),source=read('source'),rubric=read('rubric');
  assert.equal(job.schemaVersion,'fcaj.ai.message.v1');
  assert.equal(job.eventType,'AI_JOB_REQUESTED');assert.equal(job.mode,'MOCK');assert.equal(job.deadlineAt,null);
  assert.equal(source.schemaVersion,'fcaj.ai.source.github.v1');assert.equal(source.fixtureOnly,true);
  const ref=(r,name,key)=>{assert.equal(r.key,key);assert.equal(r.sha256,hash(bytes(name)));assert.equal(r.sizeBytes,bytes(name).length);assert.equal(r.contentType,'application/json');assert.equal(r.versionId,null);};
  for(const [name,key] of [['source',`inputs/${job.jobId}/source.json`],['manifest',`inputs/${job.jobId}/input-manifest.json`],['rubric',`rubrics/${job.jobId}/rubric.json`]]) ref(job.input[name],name,key);
  assert.equal(job.inputRevision,hash(bytes('manifest')));assert.deepEqual(manifest.source,job.input.source);assert.deepEqual(manifest.rubric,job.input.rubric);
  assert.equal(manifest.commitSha,source.commitSha);assert.equal(manifest.submissionId,job.submissionId);
  assert.equal(rubric.rubricVersion,job.rubricVersion);
  assert.equal(completion.eventType,'AI_JOB_COMPLETED');for(const key of identity) assert.equal(completion[key],job[key]);
  const accepted=read('http-create'),status=read('http-status'),result=read('http-result');
  assert.equal(accepted.success,true);assert.equal(accepted.data.status,'PENDING_DISPATCH');assert.equal(accepted.data.jobId,job.jobId);
  assert.equal(status.data.jobId,job.jobId);assert.equal(status.data.mode,'MOCK');
  assert.equal(status.data.status,outcome==='success'?'SUCCEEDED':'FAILED');assert.equal(completion.status,status.data.status);
  if(outcome==='success') {
    ref(completion.result,'output',`results/${job.jobId}/mock-output.json`);
    const output=read('output');for(const key of identity) assert.equal(output[key],job[key]);
    assert.equal(output.provenance.mode,'MOCK');assert.equal(result.data.model,'mock-worker');
    for(const field of ['source','inputRevision','requesterId','evidence','provenance','repoUrl']) assert.ok(!JSON.stringify(result).includes(`"${field}"`));
  } else {assert.equal(completion.error.code,'MOCK_PROCESSING_FAILED');assert.deepEqual(result,{success:false,message:'MOCK_PROCESSING_FAILED'});}
}
console.log('PASS: current synthetic samples agree on HTTP, identity, logical keys and exact artifact bytes. Production validation is covered by the Java smoke test.');
