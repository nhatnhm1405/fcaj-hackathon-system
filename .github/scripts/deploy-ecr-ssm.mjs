// CLI subprocesses only. No AWS credentials or private environment values in output.
import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync} from 'node:fs';
import {setTimeout} from 'node:timers/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
export async function deploy({image,revision,instance,aws,sleep=setTimeout,save=()=>{},log=console.log,workflowRun}){
  if(!/^582129522297\.dkr\.ecr\.ap-southeast-1\.amazonaws\.com\/fcaj-poc-backend@sha256:[0-9a-f]{64}$/.test(image||''))throw Error('Expected the PoC ECR image pinned by digest');
  if(!/^[0-9a-f]{40}$/.test(revision||''))throw Error('Expected full source revision');
  if(instance!=='i-08f9589f2c373a395')throw Error('Unexpected deployment target');
  const body=readFileSync(new URL('deploy-ecr-host.sh',import.meta.url)).toString('base64');
  // Inserted values are validated above; body is only the checked-in script.
  const command=`printf '%s' '${body}' | base64 --decode | bash -s -- '${image}' '${revision}'`;
  const sent=aws(['ssm','send-command','--instance-ids',instance,'--document-name','AWS-RunShellScript','--timeout-seconds','60','--parameters',JSON.stringify({commands:[command],executionTimeout:['900']})]);
const commandId=sent.Command.CommandId;
log(`SSM command: ${commandId}`);
let result;
for(let attempt=0;attempt<200;attempt++){
  await sleep(5000);
  try{result=aws(['ssm','get-command-invocation','--command-id',commandId,'--instance-id',instance]);}
  catch(error){if(error.stderr?.toString().includes('InvocationDoesNotExist'))continue;throw Error('Cannot read SSM status; inspect command '+commandId);}
  if(['Pending','InProgress','Delayed'].includes(result.Status))continue;
  break;
}
// Deliberately exclude SSM stdout/stderr: application startup can contain private values.
const manifest={revision,image,instance,commandId,status:result?.Status||'Unknown',workflowRun,verifiedAt:new Date().toISOString()};
save(manifest);
if(result?.Status!=='Success'||result.ResponseCode!==0)throw Error('Deployment not verified; inspect SSM command '+commandId);
log('Verified runtime image, health and revision. Release manifest saved.');
return manifest;
}
if(process.argv[1]&&pathToFileURL(resolve(process.argv[1])).href===import.meta.url){
  const aws=(args)=>JSON.parse(execFileSync('aws',[...args,'--region','ap-southeast-1','--output','json','--no-cli-pager'],{encoding:'utf8',stdio:['ignore','pipe','pipe']}));
  await deploy({image:process.env.DEPLOY_IMAGE,revision:process.env.GITHUB_SHA,instance:process.env.AWS_POC_INSTANCE_ID,workflowRun:process.env.GITHUB_RUN_ID,aws,save:manifest=>writeFileSync('release-manifest.json',JSON.stringify(manifest,null,2))});
}
