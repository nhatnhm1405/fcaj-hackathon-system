import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
import { splitSql, inspectSql } from './inspect-dump.mjs';

// Generate private statement files only inside the ignored lab working directory.
try {
  const [dump, manifestPath, output] = process.argv.slice(2);
  const bytes = readFileSync(dump);
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  if (createHash('sha256').update(bytes).digest('hex') !== manifest.file.sha256) throw new Error();
  const sql = bytes.toString('utf8');
  const tables = inspectSql(sql);
  if (Object.keys(tables).length !== manifest.totals.tables) throw new Error();
  mkdirSync(output); // exclusive directory creation; never overwrite a prior run
  writeFileSync(join(output, 'expected-counts.tsv'),
    Object.entries(tables).map(([name, table]) => `${name}\t${table.rows}`).join('\n') + '\n', { flag: 'wx' });
  let kept = 0, omitted = 0;
  for (let statement of splitSql(sql, ';')) {
    statement = statement.replace(/^(?:\s*--[^\n]*(?:\n|$))*/, '').trim();
    if (!statement) continue;
    if (/^(?:CREATE DATABASE\b|USE\b|DROP TABLE\b|LOCK TABLES\b|UNLOCK TABLES\b)/i.test(statement)) {
      omitted++; continue;
    }
    if (/^\/\*!\d+\s+ALTER TABLE `\w+` (?:DISABLE|ENABLE) KEYS\s*\*\/$/i.test(statement)) {
      omitted++; continue; // InnoDB; no need to toggle MyISAM bulk-load keys
    }
    const setting = statement.match(/^\/\*!\d+\s+(SET [\s\S]+?)\s*\*\/$/i)?.[1];
    if (setting) {
      // Only the known session settings of this reviewed dump, never GLOBAL SET.
      if (!/^SET (?:NAMES utf8mb4|(?:@?(?:OLD_)?(?:CHARACTER_SET_CLIENT|CHARACTER_SET_RESULTS|COLLATION_CONNECTION|TIME_ZONE|UNIQUE_CHECKS|FOREIGN_KEY_CHECKS|SQL_MODE|SQL_NOTES)|@saved_cs_client)\s*=)/i.test(setting)) throw new Error();
      statement = setting;
    } else if (!/^(?:CREATE TABLE\b|INSERT INTO\b)/i.test(statement)) {
      throw new Error();
    }
    writeFileSync(join(output, String(kept++).padStart(4, '0') + '.sql'), statement, { flag: 'wx' });
  }
  console.log(JSON.stringify({ keptStatements: kept, omittedCatalogResetLockStatements: omitted }));
} catch {
  console.error('Lab preparation failed. No SQL was executed; private source values are not displayed.');
  process.exitCode = 1;
}
