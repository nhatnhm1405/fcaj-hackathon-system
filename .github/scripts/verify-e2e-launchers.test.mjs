import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {verifyLaunchers} from './verify-e2e-launchers.mjs';

function checkout(t, {separator = '\\', stale = false} = {}) {
  const prefix = path.resolve(tmpdir(), 'e2e-launcher-check-');
  const root = mkdtempSync(prefix);
  t.after(() => {
    assert.ok(path.resolve(root).startsWith(prefix), 'Cleanup must stay in the created temporary checkout');
    rmSync(root, {recursive: true, force: true});
  });
  const modules = [['back-end/src/seal-api', 'pom.xml'], ['front-end/src/seal-web', 'package.json']];
  for (const [directory, marker] of modules) {
    mkdirSync(path.join(root, directory), {recursive: true});
    writeFileSync(path.join(root, directory, marker), 'synthetic module');
  }
  // Empty old folders can survive a rename and must not make the stale path pass.
  for (const [directory] of modules) mkdirSync(path.join(root, directory.replace('seal-', 'fcaj-')), {recursive: true});
  const file = 'demo.bat';
  const [backend, frontend] = modules.map(([directory]) => (stale ? directory.replace('seal-', 'fcaj-') : directory).replaceAll('/', separator));
  writeFileSync(path.join(root, file), `@echo off\r\nset "BACKEND=%ROOT%${separator}${backend}"\r\nset "FRONTEND=%ROOT%${separator}${frontend}"\r\n`);
  return {root, files: [file]};
}

for (const separator of ['\\', '/']) {
  test(`accept existing modules with ${separator === '\\' ? 'Windows' : 'POSIX'} separators`, t => {
    const {root, files} = checkout(t, {separator});
    assert.equal(verifyLaunchers(root, files), 1);
  });
  test(`reject stale module paths with ${separator === '\\' ? 'Windows' : 'POSIX'} separators despite empty old folders`, t => {
    const {root, files} = checkout(t, {separator, stale: true});
    assert.throws(() => verifyLaunchers(root, files), /Missing BACKEND module/);
  });
}

test('allow application changes without freezing a historical source snapshot', t => {
  const {root, files} = checkout(t);
  writeFileSync(path.join(root, 'back-end/src/seal-api/pom.xml'), '<project>legitimate future change</project>');
  assert.equal(verifyLaunchers(root, files), 1);
});
