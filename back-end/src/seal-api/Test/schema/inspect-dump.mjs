import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

// Bounded reader for the plain mysqldump / Hibernate export formats used here.
// No SQL is executed. INSERT values are counted in memory and never returned.
export function splitSql(text, separator, parentheses = false) {
  const parts = [];
  let start = 0, quote = '', depth = 0;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (quote) {
      if (ch === '\\') { i++; continue; }
      if (ch === quote && text[i + 1] === quote) { i++; continue; }
      if (ch === quote) quote = '';
      continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') { quote = ch; continue; }
    if (ch === '/' && text[i + 1] === '*') {
      const end = text.indexOf('*/', i + 2);
      if (end < 0) throw new Error('Unterminated SQL comment');
      i = end + 1;
      continue;
    }
    if (ch === '#' || (ch === '-' && text[i + 1] === '-' && /\s/.test(text[i + 2] ?? ''))) {
      const end = text.indexOf('\n', i + 1);
      i = end < 0 ? text.length : end;
      continue;
    }
    if (parentheses && ch === '(') depth++;
    if (parentheses && ch === ')') depth--;
    if (depth < 0) throw new Error('Unbalanced SQL parentheses');
    if (ch === separator && depth === 0) {
      parts.push(text.slice(start, i).trim());
      start = i + 1;
    }
  }
  if (quote || depth !== 0) throw new Error('Unterminated SQL value or parentheses');
  if (text.slice(start).trim()) parts.push(text.slice(start).trim());
  return parts;
}

const identifier = '(?:`[A-Za-z_][A-Za-z0-9_]*`|[A-Za-z_][A-Za-z0-9_]*)';
const bare = value => value.replaceAll('`', '').toLowerCase();
const columnNames = value => splitSql(value, ',', true).map(c => bare(c.trim())).join(',');
const typeName = value => value.toLowerCase().replace(/\s+/g, '')
  .replace(/^integer$/, 'int').replace(/^bit\(1\)$/, 'bit');

function addDefinition(table, definition) {
  let match = definition.match(new RegExp(`^(${identifier})\\s+([a-z]+(?:\\s*\\([^)]*\\))?)(.*)$`, 'is'));
  if (match && !/^(primary|unique|key|constraint|foreign|check)$/i.test(bare(match[1]))) {
    const rest = match[3];
    const defaultValue = rest.match(/\bdefault\s+((?:current_timestamp(?:\(\d*\))?)|null|'(?:[^'\\]|\\.)*'|[-\d.]+)/i)?.[1] ?? 'null';
    const onUpdate = rest.match(/\bon update\s+(current_timestamp(?:\(\d*\))?)/i)?.[1] ?? '';
    const unknown = rest.replace(/\bdefault\s+(?:current_timestamp(?:\(\d*\))?|null|'(?:[^'\\]|\\.)*'|[-\d.]+)/gi, '')
      .replace(/\bon update\s+current_timestamp(?:\(\d*\))?/gi, '')
      .replace(/\bnot null\b|\bnull\b|\bauto_increment\b/gi, '').trim();
    if (unknown) throw new Error('Unsupported column attributes; inspect DDL privately');
    const name = bare(match[1]);
    if (table.columns[name]) throw new Error('Duplicate column in DDL');
    table.columns[name] = {
      type: typeName(match[2]), nullable: !/\bnot null\b/i.test(rest),
      identity: /\bauto_increment\b/i.test(rest), default: defaultValue.toLowerCase(), onUpdate: onUpdate.toLowerCase(),
    };
    return;
  }
  if ((match = definition.match(/^primary key\s*\(([^)]+)\)$/i))) {
    table.primaryKey = columnNames(match[1]); return;
  }
  if ((match = definition.match(/^(?:unique key `[^`]+`|constraint \S+ unique)\s*\(([^)]+)\)$/i))) {
    table.unique.push(columnNames(match[1])); return;
  }
  if ((match = definition.match(new RegExp(`^(?:constraint \\S+ )?foreign key\\s*\\(([^)]+)\\)\\s+references\\s+(${identifier})\\s*\\(([^)]+)\\)(.*)$`, 'is')))) {
    const actions = match[4].trim();
    const deleted = actions.match(/on delete\s+(cascade|restrict|no action|set null)/i)?.[1]?.toLowerCase() ?? 'restrict';
    const updated = actions.match(/on update\s+(cascade|restrict|no action|set null)/i)?.[1]?.toLowerCase() ?? 'restrict';
    if (actions.replace(/on (?:delete|update)\s+(?:cascade|restrict|no action|set null)/gi, '').trim()) {
      throw new Error('Unsupported FK options');
    }
    table.foreignKeys.push(`${columnNames(match[1])}->${bare(match[2])}(${columnNames(match[3])});delete=${deleted.replace('no action', 'restrict')};update=${updated.replace('no action', 'restrict')}`);
    return;
  }
  if (/^key `[^`]+`\s*\([^)]+\)$/i.test(definition)) {
    table.secondaryIndexes.push(definition.replace(/`[^`]+`/, '').replaceAll('`', '').replace(/\s+/g, ' ').toLowerCase());
    return;
  }
  throw new Error('Unsupported DDL definition; no data printed');
}

export function inspectSql(sql) {
  const tables = {};
  const ddl = [...sql.matchAll(/(?:^|\n)\s*create table\s+(`?[A-Za-z_][A-Za-z0-9_]*`?)\s*\(([\s\S]*?)\)\s*engine=([^;]+);/gi)];
  for (const match of ddl) {
    const name = bare(match[1]);
    if (tables[name]) throw new Error('Duplicate table in DDL');
    const options = match[3];
    const table = { name: match[1].replaceAll('`', ''), columns: {}, primaryKey: '', unique: [], foreignKeys: [], secondaryIndexes: [],
      rows: 0, engine: options.match(/^\w+/)?.[0], charset: options.match(/charset=(\w+)/i)?.[1] ?? null,
      collation: options.match(/collate=(\w+)/i)?.[1] ?? null };
    for (const definition of splitSql(match[2], ',', true)) addDefinition(table, definition);
    tables[name] = table;
  }
  // Hibernate emits constraints separately; mysqldump embeds them in CREATE TABLE.
  for (const match of sql.matchAll(/(?:^|\n)\s*alter table\s+(`?[A-Za-z_][A-Za-z0-9_]*`?)\s+add\s+([^;]+);/gi)) {
    const table = tables[bare(match[1])];
    if (!table) throw new Error('ALTER references an unknown table');
    addDefinition(table, match[2].replace(/\s+/g, ' ').trim());
  }
  // Split statements lexically so quotes, commas, semicolons and JSON in data
  // cannot affect tuple counts. This tool supports mysqldump's INSERT ... VALUES.
  for (const raw of splitSql(sql, ';')) {
    const statement = raw.replace(/^(?:\s*(?:--[^\n]*(?:\n|$)|\/\*[\s\S]*?\*\/))*/, '').trim();
    const match = statement.match(new RegExp(`^insert into\\s+(${identifier})\\s+values\\s+([\\s\\S]+)$`, 'i'));
    if (!match) {
      if (/^insert\b/i.test(statement)) throw new Error('Unsupported INSERT format');
      continue;
    }
    const table = tables[bare(match[1])];
    if (!table) throw new Error('INSERT references an unknown table');
    const tuples = splitSql(match[2], ',', true);
    for (const tuple of tuples) {
      if (!tuple.startsWith('(') || !tuple.endsWith(')')) throw new Error('Malformed INSERT tuple');
      if (splitSql(tuple.slice(1, -1), ',', true).length !== Object.keys(table.columns).length) {
        throw new Error('INSERT tuple width differs from table column count');
      }
    }
    table.rows += tuples.length;
  }
  if (Object.keys(tables).length !== (sql.match(/^\s*create table\b/gim) ?? []).length) {
    throw new Error('Not every CREATE TABLE was recognized');
  }
  return tables;
}

export function compareSchemas(actual, expected) {
  const differences = [];
  const equivalent = [];
  for (const name of [...new Set([...Object.keys(actual), ...Object.keys(expected)])].sort()) {
    const a = actual[name], e = expected[name];
    if (!a || !e) { differences.push({ table: name, field: a ? 'extra-table' : 'missing-table' }); continue; }
    let matches = true;
    for (const column of [...new Set([...Object.keys(a.columns), ...Object.keys(e.columns)])].sort()) {
      if (JSON.stringify(a.columns[column]) !== JSON.stringify(e.columns[column])) {
        differences.push({ table: name, column, actual: a.columns[column] ?? null, expected: e.columns[column] ?? null });
        matches = false;
      }
    }
    for (const key of ['primaryKey', 'unique', 'foreignKeys']) {
      const normalize = v => JSON.stringify(Array.isArray(v) ? [...v].sort() : v);
      if (normalize(a[key]) !== normalize(e[key])) {
        differences.push({ table: name, field: key, actual: a[key], expected: e[key] }); matches = false;
      }
    }
    if (matches) equivalent.push(name);
  }
  return { equivalentTables: equivalent, differences };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const [dumpPath, referencePath, outputPath] = process.argv.slice(2);
    if (!dumpPath || !referencePath || !outputPath) throw new Error('Expected dump, reference SQL, NEW report JSON path');
    const bytes = readFileSync(dumpPath);
    const sql = bytes.toString('utf8');
    if (!sql.startsWith('-- MySQL dump')) throw new Error('Expected a plain MySQL dump');
    const actual = inspectSql(sql);
    const expected = inspectSql(readFileSync(referencePath, 'utf8'));
    const report = {
      file: { bytes: bytes.length, sha256: createHash('sha256').update(bytes).digest('hex'),
        serverVersion: sql.match(/^-- Server version\s+(.+)$/m)?.[1]?.trim(),
        dumpCompleted: sql.match(/^-- Dump completed on (.+)$/m)?.[1]?.trim() },
      safety: { createDatabase: (sql.match(/^CREATE DATABASE\b/gm) ?? []).length,
        use: (sql.match(/^USE\b/gm) ?? []).length, dropTable: (sql.match(/^DROP TABLE\b/gm) ?? []).length,
        lockTables: (sql.match(/^LOCK TABLES\b/gm) ?? []).length },
      totals: { tables: Object.keys(actual).length, rows: Object.values(actual).reduce((n,t) => n + t.rows, 0),
        columns: Object.values(actual).reduce((n,t) => n + Object.keys(t.columns).length, 0),
        foreignKeys: Object.values(actual).reduce((n,t) => n + t.foreignKeys.length, 0),
        unique: Object.values(actual).reduce((n,t) => n + t.unique.length, 0) },
      tables: Object.entries(actual).map(([name,t]) => ({ name, rows: t.rows, columns: Object.keys(t.columns).length,
        engine: t.engine, charset: t.charset, collation: t.collation, secondaryIndexes: t.secondaryIndexes.length })),
      comparison: compareSchemas(actual, expected),
      limitations: ['Static normalized DDL comparison only; not runtime acceptance.',
        'Table and identifier case folded for comparison; column order ignored.',
        'Secondary indexes and inherited charset/collation require runtime review.',
        'No data values, FK orphan checks or relationship validation in this report.'],
    };
    writeFileSync(outputPath, JSON.stringify(report, null, 2) + '\n', { flag: 'wx' });
    console.log(JSON.stringify({ totals: report.totals, equivalentTables: report.comparison.equivalentTables.length,
      differences: report.comparison.differences.length }));
  } catch {
    // Exceptions must not echo statements, tuple values or sensitive source text.
    console.error('Dump inspection failed; verify supported format and use a new output path. No SQL was executed.');
    process.exitCode = 1;
  }
}
