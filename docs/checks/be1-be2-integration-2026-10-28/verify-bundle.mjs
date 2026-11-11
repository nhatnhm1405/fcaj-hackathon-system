import {readFileSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import path from 'node:path';

const args=process.argv.slice(2);
if(args.some(arg=>arg!=='--staged'))throw new Error('Use no arguments for HEAD, or --staged before commit');
const root=execFileSync('git',['rev-parse','--show-toplevel'],{encoding:'utf8'}).trim();
const prefix=args.includes('--staged')?':':'HEAD:';
const folder='docs/checks/be1-be2-integration-2026-10-28';
let count=0;
for(const manifest of ['source-files.sha256','SHA256SUMS']) {
  for(const line of readFileSync(path.join(root,folder,manifest),'utf8').split(/\r?\n/)) {
    if(!line.trim())continue;
    const match=line.match(/^([a-f0-9]{64})  (.+)$/);
    if(!match)throw new Error(`Invalid checksum entry in ${manifest}`);
    const [,expected,file]=match;
    if(file.includes('\\')||file.startsWith('/')||file.includes(':')||file.split('/').includes('..'))throw new Error('Unsafe checksum path');
    const bytes=execFileSync('git',['show',prefix+file],{cwd:root,maxBuffer:16*1024*1024,stdio:['ignore','pipe','pipe']});
    if(createHash('sha256').update(bytes).digest('hex')!==expected)throw new Error(`Checksum mismatch: ${file}`);
    count++;
  }
}
console.log(`PASS: ${count} checksum entries match ${prefix===':'?'staged':'committed HEAD'} Git bytes; independent of working-copy line endings.`);
