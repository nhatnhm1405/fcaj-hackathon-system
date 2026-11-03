import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inspectSql, compareSchemas } from './inspect-dump.mjs';

const ddl = `CREATE TABLE \`example\` (
  \`id\` int NOT NULL AUTO_INCREMENT,
  \`payload\` text,
  PRIMARY KEY (\`id\`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;`;

test('counts tuples without treating punctuation and escaped strings as rows', () => {
  const sql = ddl + String.raw`
INSERT INTO ` + '`example`' + String.raw` VALUES (1,'a,b;()'),(2,'it\'s (JSON),;'),(3,'{"x":[1,2]}');`;
  const table = inspectSql(sql).example;
  assert.equal(table.rows, 3);
  assert.deepEqual(Object.keys(table.columns), ['id', 'payload']);
  assert.equal('values' in table, false);
  assert.equal(JSON.stringify(table).includes('JSON'), false);
});

test('normalizes default NULL and MySQL equivalent type spellings', () => {
  const actual = inspectSql(ddl.replace('`payload` text', '`payload` text DEFAULT NULL'));
  const expected = inspectSql(ddl.replace(' int ', ' integer '));
  assert.deepEqual(compareSchemas(actual, expected).differences, []);
});

test('checks nullability, identity, lengths and column presence', () => {
  const actual = inspectSql(ddl);
  const changed = inspectSql(ddl.replace('`payload` text', '`payload` varchar(50) NOT NULL'));
  assert.equal(compareSchemas(actual, changed).differences.length, 1);
});

test('reports missing tables rather than matching counts alone', () => {
  const actual = inspectSql(ddl);
  const expected = inspectSql(ddl.replaceAll('example', 'other'));
  assert.equal(compareSchemas(actual, expected).differences.length, 2);
});

test('rejects unterminated and wrong-width INSERTs without leaking values', () => {
  assert.throws(() => inspectSql(ddl + "\nINSERT INTO example VALUES (1,'secret');\nINSERT INTO example VALUES (2,'unterminated"), /Unterminated/);
  assert.throws(() => inspectSql(ddl + "\nINSERT INTO example VALUES (1,'secret',3);"), /width differs/);
});

test('rejects unsupported attributes so they cannot disappear in comparison', () => {
  assert.throws(() => inspectSql(ddl.replace('`payload` text', '`payload` text COLLATE utf8mb4_bin')), /Unsupported column/);
});

test('compares FK rules and unique column order across dump and Hibernate formats', () => {
  const embedded = ddl.replace('PRIMARY KEY (`id`)', 'PRIMARY KEY (`id`), UNIQUE KEY `uq` (`id`,`payload`), CONSTRAINT `fk` FOREIGN KEY (`id`) REFERENCES `example` (`id`) ON DELETE CASCADE');
  const separated = ddl + '\nalter table example add constraint uq unique (id,payload);\nalter table example add constraint fk foreign key (id) references example (id) on delete cascade;';
  assert.deepEqual(compareSchemas(inspectSql(embedded), inspectSql(separated)).differences, []);
  assert.equal(compareSchemas(inspectSql(embedded), inspectSql(separated.replace('on delete cascade', 'on delete restrict'))).differences.length, 1);
  assert.equal(compareSchemas(inspectSql(embedded), inspectSql(separated.replace('unique (id,payload)', 'unique (payload,id)'))).differences.length, 1);
});
