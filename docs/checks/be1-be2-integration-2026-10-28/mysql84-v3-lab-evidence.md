# Isolated MySQL dump migration lab

Container: `fcaj-migration-lab-eb66ed843c67` (stopped by wrapper after checks).

Server: 8.4.13; Connector/J: mysql-connector-j-9.7.0 (Revision: 0aade1f13bcc98faf7dda5c02e782481eb291f62); lower_case_table_names=1; utf8mb4 / utf8mb4_0900_ai_ci.

PASS: separate MySQL 8.0.46 source restored reviewed statements, mysqldump backup restored to separate8.4 target; original-column fingerprints match before baseline/migration. Data-bearing backup stays under ignored target.

Source dump SHA-256: ab6279c0ad77be4405ca1505c1da8b7db2a1a7c6b9e052db5b672790e1f23802.

Candidate V1 SHA-256: 91a3b9b0cb4d41ce5027fa850200848126bdfe57ba0ceef97defa1ff7928ea9d; Flyway checksum: -732525868. Checks use isolated catalogs only.

PASS: V2/V3 apply on empty and restored V1; restart no-op; all 627 legacy rows retain fingerprints over original columns; version columns initialized to zero; three job tables start empty; all 61 FK checks pass.

PASS: deploy gate rejects no-history, baseline-only, pre-V3, missing-version and checksum faults; read-only gate and packaged candidate CLI pass on restored V3.

PASS: restore 31 tables/627 rows; every table count matches dump tuples; JDBC import statements returned no warnings; all 55 FK orphan checks; every unique key duplicate check; auto-increment > max ID.

PASS: Flyway empty V1, repeat no-op, checksum rejection; Hibernate validation on new/restored schemas, missing-column rejection.

PASS: runtime column/default/collation/index/FK/engine equivalence, ignoring case, physical column order and constraint/index names.

PASS: nonempty schema migrate rejected until explicit baseline 1; no V1 rerun; baseline preserves all table row fingerprints and schema.

## Restored counts

| Table | Rows |
|---|---:|
| announcement | 0 |
| auditlog | 3 |
| hackathonevent | 2 |
| joinrequest | 0 |
| judgeassignment | 11 |
| mentorassignment | 4 |
| mentorsupportrequest | 0 |
| notification | 59 |
| participanteventhistory | 45 |
| participationaccessrequest | 1 |
| passwordresetotp | 0 |
| prize | 3 |
| reopenrequest | 0 |
| role | 4 |
| round | 2 |
| roundresult | 21 |
| roundtimer | 4 |
| roundtimernotice | 0 |
| score | 270 |
| scoringcriteria | 15 |
| scoringcriteriatemplate | 1 |
| submission | 23 |
| systemlog | 1 |
| team | 15 |
| teamevententry | 16 |
| teaminvite | 0 |
| teammember | 45 |
| teamrejoinrequest | 1 |
| track | 4 |
| user | 62 |
| usereventrole | 15 |

Row fingerprints are SHA-256 over length-prefixed JDBC bytes, sorted by PK; compared in memory before/after baseline. No row values/hashes are published.

This lab checks migrations and entities. Backend job API integration is covered separately by AiJobIntegrationTest. No RDS, source DB or source dump was modified.
