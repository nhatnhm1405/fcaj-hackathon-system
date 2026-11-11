// Offline Compose parsing with synthetic values only. No docker run/up, AWS or TLS probe.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdirSync, mkdtempSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const moduleRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
mkdirSync(path.join(moduleRoot, 'target'), {recursive: true});
const lab = mkdtempSync(path.join(moduleRoot, 'target', 'compose-review-'));
const runtime = path.join(lab, 'synthetic.env');
const truststore = path.join(lab, 'synthetic.p12');
const sentinel = 'synthetic$literal"quotes';
writeFileSync(runtime, `SPRING_DATASOURCE_USERNAME=synthetic\nSPRING_DATASOURCE_PASSWORD=${sentinel}\n`);
writeFileSync(truststore, 'SYNTHETIC; NOT A TRUSTSTORE');
const revision = 'c'.repeat(40);
const env = {...process.env,
  CANDIDATE_REVISION: revision,
  CANDIDATE_BACKEND_IMAGE: `seal-backend-candidate:${revision}`,
  DEPLOY_RUNTIME_ENV_FILE: runtime,
  DEPLOY_RUNTIME_JDBC_URL: 'jdbc:mysql://rds.example.test:3306/fcaj_poc?sslMode=VERIFY_IDENTITY',
  DEPLOY_DB_TRUSTSTORE_FILE: truststore,
  DEPLOY_SPRING_PROFILES_ACTIVE: 'aws',
  DEPLOY_TARGET_BIND_IP: '10.20.30.40'};
const docker = process.env.DOCKER_BIN || (process.platform === 'win32' ? 'C:/Program Files/Docker/Docker/resources/bin/docker.exe' : 'docker');
for (const [file, bind] of [
  ['compose.aws.example.yml', '127.0.0.1'],
  ['compose.aws.alb-direct.example.yml', env.DEPLOY_TARGET_BIND_IP],
]) {
  const args = ['compose', '-f', path.join(moduleRoot, 'deploy', file), 'config', '--format', 'json'];
  const config = JSON.parse(execFileSync(docker, args, {env, encoding: 'utf8'}));
  assert.deepEqual(Object.keys(config.services), ['seal-backend']);
  const service = config.services['seal-backend'];
  assert.equal(service.image, env.CANDIDATE_BACKEND_IMAGE);
  assert.equal(service.environment.SPRING_PROFILES_ACTIVE, 'aws');
  assert.equal(service.environment.SPRING_DATASOURCE_URL, env.DEPLOY_RUNTIME_JDBC_URL);
  assert.equal(service.environment.APP_BUILD_REVISION, revision);
  // Compose escapes dollars in rendered config for re-ingestion. This checks the
  // canonical representation; actual container env is a separate runtime probe.
  assert.equal(service.environment.SPRING_DATASOURCE_PASSWORD, sentinel.replaceAll('$', () => '$$'), 'raw env config serialization');
  assert.equal(service.ports.length, 1);
  assert.equal(service.ports[0].host_ip, bind);
  assert.equal(service.ports[0].target, 8080);
  assert.equal(String(service.ports[0].published), '8080');
  assert.equal(service.volumes[0].target, '/run/secrets/rds-truststore.p12');
  assert.equal(service.volumes[0].read_only, true);
  console.log(`PASS ${file}: candidate, raw env, profile, revision, target binding, readonly truststore`);
}
const missingBind = {...env}; delete missingBind.DEPLOY_TARGET_BIND_IP;
let rejected = false;
try {
  execFileSync(docker, ['compose', '-f', path.join(moduleRoot, 'deploy', 'compose.aws.alb-direct.example.yml'), 'config', '-q'],
    {env: missingBind, stdio: 'pipe'});
} catch { rejected = true; }
assert.equal(rejected, true, 'ALB direct must require an explicit target interface');
console.log('PASS ALB-direct missing interface rejected; parse only, no target deployment or connectivity asserted');
