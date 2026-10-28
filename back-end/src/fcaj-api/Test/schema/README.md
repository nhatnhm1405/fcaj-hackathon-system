# Offline schema inventory

Run from `back-end/src/fcaj-api` with Java 21 and Maven Wrapper:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21.0.10'
./mvnw.cmd -B org.apache.maven.plugins:maven-dependency-plugin:3.9.0:build-classpath '-Dmdep.outputFile=target/schema-tool-classpath.txt' '-DincludeScope=test'
./Test/schema/export-schema.ps1 -JdkHome $env:JAVA_HOME -OutputDirectory './target/schema-review-new'
```

Use a **new output directory** on each run. The exporter refuses to overwrite an
existing directory. It generates a MySQL 8.4 reference DDL and a table/column/FK/
unique-key inventory with entity SHA-256 hashes. These are review artifacts, not
runtime migrations. Build products stay under the ignored `target/` directory.

Only entity sources and this utility are compiled. It never starts Spring,
seeders, Flyway or the full app. Hibernate JDBC metadata access is disabled, and
the connection provider cannot supply a connection. No datasource URL, credentials
or `.env` are loaded. An explicit offline MySQL dialect is intentional.

This command does **not** replace `./mvnw.cmd -Dspring.profiles.active=test test`
or the future MySQL migration suite. A successful export does not establish
schema compatibility with an existing database, data preservation, SQL execution
on MySQL, or successful startup of the full backend.

Review generated DDL against Java mappings and real DB metadata before adopting
it as V1. Choose case handling and charset/collation for the lab and RDS; verify
the resulting PK/FK/indexes/defaults and ORM queries on MySQL 8.4. SQL defaults
must not be inferred from Lombok builder defaults or Java lifecycle callbacks.

## Inspect a provided dump without executing SQL

From the repository root:

```powershell
node --test back-end/src/fcaj-api/Test/schema/inspect-dump.test.mjs
node back-end/src/fcaj-api/Test/schema/inspect-dump.mjs 'E:/FA26/AWS/seal_hackathon.bak' docs/checks/schema-inventory-2026-10-27/hibernate-mysql84-reference.sql ./back-end/src/fcaj-api/target/dump-review-new.json
```

The reader supports this project's plain `mysqldump` CREATE TABLE and INSERT
VALUES format and the Hibernate reference export. It counts tuples while respecting
SQL string escaping, nested parentheses and comments; it does not return data
values. Output creation is exclusive, so use a new path for each report.

DDL comparison normalizes table/column case, INT/INTEGER, BIT/BIT(1), implicit
nullable DEFAULT NULL and FK NO ACTION/RESTRICT. It checks column type, nullability,
identity, defaults/ON UPDATE, ordered PK/unique/FK columns, referenced tables and
FK update/delete rules. Column order, constraint names, secondary-index definitions
and database-inherited charset/collation are outside the equivalence assertion.
The report lists table charset/collation and secondary-index counts separately.

This bounded static reader is not a general SQL parser, import sanitizer or
baseline approval tool. It does not run SQL, create an import copy, validate row
values/relationships, or prove a successful restore. Raw backup files and any
future import copies containing data must remain outside Git.

## Run the isolated Docker restore/migration lab

Requires the resolved Maven classpath, Node.js, Java 21 and Docker Desktop Linux
Engine. From `back-end/src/fcaj-api`:

```powershell
./Test/schema/run-dump-lab.ps1 -DumpPath 'E:/FA26/AWS/seal_hackathon.bak' -JdkHome 'C:/Program Files/Java/jdk-21.0.10'
```

The runner is tied to the reviewed dump SHA-256 in the static manifest and the
official image digest recorded in the script. A changed dump requires fresh
inspection first. It creates a new container on a dynamic port bound only to
127.0.0.1, with case handling 1 and utf8mb4_0900_ai_ci. Credentials are generated
for this lab and passed through process environment, not command arguments/logs.
No developer `.env` is used.

`prepare-dump-lab.mjs` writes data-bearing statement files only under ignored
`target/<lab-id>/restore-statements`. It omits catalog selection/creation, table
reset, locks and MyISAM key toggles by statement classification. Remaining SQL
runs in `fcaj_restore_lab` on the NEW container. The separate empty catalog
`fcaj_empty_lab` runs the current source V1 through Flyway.

Checks cover per-table counts, zero JDBC import warnings, orphan FKs, duplicate
unique keys, auto-increment, runtime schema/index equivalence, Hibernate validation,
repeat migration, checksum/schema fault probes, rejection of implicit baseline,
explicit baseline after equivalence checks, and data fingerprints before/after.

The wrapper stops the container in `finally` and retains its data for review.
It does not remove containers/volumes/images or contact developer MySQL/RDS.
Sanitized evidence is under `target/<lab-id>/lab-evidence.md`. Full app/API tests
and runtime configuration are separate acceptance steps. This lab compiles
entities only and uses a test Hibernate pool; it is not a production runner.
