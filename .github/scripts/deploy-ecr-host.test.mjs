import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

test('host retains candidate image for Compose verification after the child gate exits', () => {
  const root = mkdtempSync(path.join(tmpdir(), 'fcaj-host-test-'));
  const unix = value => value.replaceAll('\\', '/');
  const bin = path.join(root, 'bin'); mkdirSync(bin);
  const release = unix(path.join(root, 'releases'));
  const settings = unix(path.join(root, 'settings.sh'));
  const image = '582129522297.dkr.ecr.ap-southeast-1.amazonaws.com/fcaj-poc-backend@sha256:' + 'a'.repeat(64);
  const revision = 'b'.repeat(40);
  const script = readFileSync(new URL('deploy-ecr-host.sh', import.meta.url), 'utf8')
    .replaceAll('/etc/seal/deploy-settings.sh', settings)
    .replaceAll('/var/lib/seal/releases', release);
  writeFileSync(path.join(root, 'host.sh'), script);
  writeFileSync(settings, 'DEPLOY_COMPOSE_FILE=synthetic.yml\nDEPLOY_DB_MODE=verify-only\n');
  const stub = (name, source) => writeFileSync(path.join(bin, name), '#!/bin/bash\nset -eu\n' + source, {mode: 0o755});
  stub('aws', 'echo SYNTHETIC_TEST_TOKEN\n');
  stub('curl', 'printf \'{"revision":"%s"}\\n\' "$TEST_REVISION"\n');
  stub('python3', 'cat >/dev/null\n');
  stub('install', 'true\n');
  stub('docker', `case "$1" in
login) cat >/dev/null ;;
pull|rm) ;;
create) echo test-extract-container ;;
cp) printf 'export CANDIDATE_BACKEND_IMAGE="$DEPLOY_IMAGE"\\n' > "$3" ;;
compose)
  test "\${CANDIDATE_BACKEND_IMAGE:?Candidate image missing in host process}" = "$TEST_IMAGE"
  echo test-runtime-container ;;
inspect)
  if [[ "$*" == *Config.Image* ]]; then echo "$TEST_IMAGE"; else echo sha256:test-config; fi ;;
image) echo sha256:test-config ;;
*) exit 90 ;;
esac\n`);
  const env = {...process.env, TEST_IMAGE:image, TEST_REVISION:revision};
  delete env.CANDIDATE_BACKEND_IMAGE;
  delete env.DEPLOY_IMAGE;
  const inheritedPath = process.env.PATH || process.env.Path;
  for (const key of Object.keys(env)) if (key.toLowerCase() === 'path') delete env[key];
  env.PATH = bin + path.delimiter + inheritedPath;
  const shellBin = unix(bin).replace(/^([A-Za-z]):/, (_, drive) => '/' + drive.toLowerCase());
  const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
  try {
    const result = spawnSync(bash, ['-c', `export PATH='${shellBin}':$PATH; exec bash "$@"`, 'test', unix(path.join(root, 'host.sh')), image, revision], {env, encoding:'utf8', timeout:15000});
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.ok(!result.stdout.includes('SYNTHETIC_TEST_TOKEN'));
  } finally {
    assert.equal(path.dirname(path.resolve(root)), path.resolve(tmpdir()));
    assert.ok(path.basename(root).startsWith('fcaj-host-test-'));
    rmSync(root, {recursive:true, force:true});
  }
});
