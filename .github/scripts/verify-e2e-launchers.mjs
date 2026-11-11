import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFileSync, statSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Validate paths only: never execute a demo launcher or its database reset.
export function verifyLaunchers(root, files) {
  assert.ok(files.length > 0, 'No tracked E2E BAT launchers found');
  for (const file of files) {
    const text = readFileSync(path.join(root, file), 'utf8').replaceAll('\r\n', '\n');
    const bindings = [...text.matchAll(/^set "(BACKEND|FRONTEND)=%ROOT%[\\/]([^"\n]+)"$/gim)];
    assert.deepEqual(bindings.map(m => m[1].toUpperCase()).sort(), ['BACKEND', 'FRONTEND'], `Expected one binding for each module: ${file}`);
    for (const [, name, relative] of bindings) {
      const role = name.toUpperCase();
      const directory = path.resolve(root, relative.replaceAll('\\', '/'));
      const withinRoot = path.relative(root, directory);
      assert.ok(withinRoot !== '..' && !withinRoot.startsWith(`..${path.sep}`) && !path.isAbsolute(withinRoot), `Module path escapes checkout: ${file}`);
      const marker = path.join(directory, role === 'BACKEND' ? 'pom.xml' : 'package.json');
      assert.ok(statSync(marker, {throwIfNoEntry: false})?.isFile(), `Missing ${role} module referenced by ${file}: ${relative}`);
    }
  }
  return files.length;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
  const files = execFileSync('git', ['ls-files', '-z', '--', 'E2E testing'], {cwd: root}).toString().split('\0').filter(p => p.endsWith('.bat'));
  console.log(`PASS: ${verifyLaunchers(root, files)} E2E launchers reference existing modules; no demo process was started.`);
}
