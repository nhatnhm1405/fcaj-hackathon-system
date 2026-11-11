import {test} from 'node:test';
import assert from 'node:assert/strict';
import {deploy} from './deploy-ecr-ssm.mjs';
const target={image:'582129522297.dkr.ecr.ap-southeast-1.amazonaws.com/fcaj-poc-backend@sha256:'+'a'.repeat(64),revision:'b'.repeat(40),instance:'i-08f9589f2c373a395',sleep:async()=>{},log:()=>{}};
for(const invalid of [{image:target.image.replace(/@sha256:.*/,':latest')},{revision:"$(echo injected)"},{instance:'i-another-target'},{image:target.image+"'; echo injected"}])test('reject unsafe target '+JSON.stringify(invalid),async()=>{
  let calls=0;
  await assert.rejects(deploy({...target,...invalid,aws:()=>{calls++;}}));
  assert.equal(calls,0);
});
test('poll to verified success and persist only nonsecret metadata',async()=>{
  const calls=[],logs=[];let saved,polls=0;
  const manifest=await deploy({...target,log:s=>logs.push(s),save:m=>saved=m,aws:args=>{
    calls.push(args);
    if(args[1]==='send-command')return {Command:{CommandId:'test-command'}};
    return ++polls===1?{Status:'InProgress'}:{Status:'Success',ResponseCode:0,StandardOutputContent:'PRIVATE_SENTINEL',StandardErrorContent:'PRIVATE_SENTINEL'};
  }});
  assert.equal(manifest.status,'Success');assert.equal(saved.commandId,'test-command');
  const parameters=JSON.parse(calls[0][calls[0].indexOf('--parameters')+1]);
  assert.equal(parameters.commands.length,1);
  assert.ok(parameters.commands[0].endsWith(`'${target.image}' '${target.revision}'`));
  assert.ok(!JSON.stringify({saved,logs}).includes('PRIVATE_SENTINEL'));
});
for(const result of [{Status:'Failed',ResponseCode:1},{Status:'Success',ResponseCode:1}])test('reject unsuccessful command '+JSON.stringify(result),async()=>{
  let saved;
  await assert.rejects(deploy({...target,save:m=>saved=m,aws:args=>args[1]==='send-command'?{Command:{CommandId:'test-command'}}:result}),/not verified/);
  assert.equal(saved.status,result.Status);
});
test('retry invocation propagation delay',async()=>{
  let polls=0;
  await deploy({...target,aws:args=>{
    if(args[1]==='send-command')return {Command:{CommandId:'test-command'}};
    if(++polls===1)throw Object.assign(new Error('not yet'),{stderr:Buffer.from('InvocationDoesNotExist')});
    return {Status:'Success',ResponseCode:0};
  }});
  assert.equal(polls,2);
});
