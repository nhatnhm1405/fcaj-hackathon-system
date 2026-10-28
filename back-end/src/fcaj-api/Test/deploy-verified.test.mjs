import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,readFileSync,mkdirSync,existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
const bash=process.platform==='win32'?'C:/Program Files/Git/bin/bash.exe':'bash';
const script=path.resolve(import.meta.dirname,'deploy-verified.sh');
const scenarios=[
  {name:'default verify-only succeeds before replacement',success:true},
  {name:'failed gate preserves backend',failAction:'verify-deploy'},
  {name:'new DB initialization precedes verification and replacement',mode:'initialize-empty',success:true},
  {name:'failed initialization never verifies or replaces backend',mode:'initialize-empty',failAction:'initialize-empty'},
  {name:'failed verification after initialization preserves backend',mode:'initialize-empty',failAction:'verify-deploy'},
  {name:'unknown mode is rejected before build',mode:'migrate-all',noBuild:true},
  {name:'compose precheck failure never initializes DB',mode:'initialize-empty',precheckFailure:true},
  {name:'dirty candidate never initializes DB',mode:'initialize-empty',dirty:true,noBuild:true},
];
for(const scenario of scenarios) test(scenario.name,()=>{
  const root=mkdtempSync(path.join(tmpdir(),'deploy-gate-')), bin=path.join(root,'bin');mkdirSync(bin);
  const record=path.join(root,'calls').replaceAll('\\','/');
  writeFileSync(path.join(bin,'git'),'#!/bin/bash\nif [ "$1" = rev-parse ]; then echo candidate; fi\nif [ "$1" = diff ] && [ "${TEST_DIRTY:-}" = true ]; then exit 1; fi\n',{mode:0o755});
  // Shell ordering only; real compose/schema/TLS validation needs separate checks.
  writeFileSync(path.join(bin,'python3'),'#!/bin/bash\ncat >/dev/null\nif [ "${TEST_PRECHECK_FAILURE:-}" = true ]; then exit 1; fi\n',{mode:0o755});
  writeFileSync(path.join(bin,'docker'),`#!/bin/bash\nprintf '%s\\n' "$*" >> '${record}'\nif [ "$1" = run ] && [[ "$*" == *" $TEST_FAIL_ACTION "* ]]; then exit 1; fi\nif [ "$1" = compose ] && [ "$4" = config ]; then echo '{}'; fi\n`,{mode:0o755});
  const env={...process.env,CANDIDATE_REVISION:'candidate',DEPLOY_COMPOSE_FILE:'target.yml',DEPLOY_MIGRATION_ENV_FILE:'private.env',DEPLOY_DB_CATALOG:'lab',DEPLOY_RUNTIME_JDBC_URL:'jdbc:mysql://127.0.0.1/lab',TEST_FAIL_ACTION:scenario.failAction||'',TEST_PRECHECK_FAILURE:String(!!scenario.precheckFailure),TEST_DIRTY:String(!!scenario.dirty)};
  delete env.DEPLOY_DB_MODE;
  if(scenario.mode)env.DEPLOY_DB_MODE=scenario.mode;
  const priorPath=process.env.PATH||process.env.Path;
  for(const k of Object.keys(env))if(k.toLowerCase()==='path')delete env[k];
  env.PATH=bin+path.delimiter+priorPath;
  const shellBin=bin.replaceAll('\\','/').replace(/^([A-Za-z]):/,(_,drive)=>'/'+drive.toLowerCase());
  const result=spawnSync(bash,['-c',`export PATH='${shellBin}':$PATH; exec bash "$1"`,'test',script],{env,encoding:'utf8'});
  assert.equal(result.status,scenario.success?0:1,result.stderr);
  const calls=existsSync(record)?readFileSync(record,'utf8'):'';
  assert.equal(calls.includes('up -d --no-build'),!!scenario.success,calls);
  assert.ok(!calls.includes('stop')&&!calls.includes('remove'),calls);
  if(scenario.noBuild)assert.equal(calls,'');
  if(scenario.success)assert.ok(calls.indexOf('verify-deploy')<calls.indexOf('up -d --no-build'),calls);
  if(scenario.success&&scenario.mode==='initialize-empty')assert.ok(calls.indexOf('initialize-empty')<calls.indexOf('verify-deploy'),calls);
  if(!scenario.mode)assert.ok(!calls.includes('initialize-empty'),calls);
  if(scenario.failAction==='initialize-empty'||scenario.precheckFailure)assert.ok(!calls.includes('verify-deploy'),calls);
  if(scenario.precheckFailure)assert.ok(!calls.includes('\nrun ')&&!calls.startsWith('run '),calls);
});
