#!/usr/bin/env bash
# Operator runs this in a private Session Manager terminal and types the password.
# Metadata only: no application rows, DDL, migration, grants or credential changes.
set -euo pipefail
test -r /etc/seal/rds-ca-bundle.pem
sql=$(cat <<'SQL'
SET SESSION TRANSACTION READ ONLY;
START TRANSACTION READ ONLY;
SELECT VERSION() AS mysql_version, @@lower_case_table_names AS lower_case_table_names;
SHOW SESSION STATUS LIKE 'Ssl_cipher';
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='fcaj_poc';
SELECT TABLE_TYPE, COUNT(*) AS object_count
FROM information_schema.TABLES WHERE TABLE_SCHEMA='fcaj_poc' GROUP BY TABLE_TYPE;
SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES
WHERE TABLE_SCHEMA='fcaj_poc' ORDER BY TABLE_NAME;
SELECT 'routines' AS object_type, COUNT(*) AS object_count FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA='fcaj_poc'
UNION ALL SELECT 'triggers', COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA='fcaj_poc'
UNION ALL SELECT 'events', COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA='fcaj_poc';
SET @history_query = IF(EXISTS(
  SELECT 1 FROM information_schema.TABLES
  WHERE TABLE_SCHEMA='fcaj_poc' AND TABLE_NAME='flyway_schema_history'
), 'SELECT installed_rank,version,type,success FROM fcaj_poc.flyway_schema_history ORDER BY installed_rank',
   'SELECT ''FLYWAY_HISTORY_ABSENT'' AS migration_history');
PREPARE history_statement FROM @history_query;
EXECUTE history_statement;
DEALLOCATE PREPARE history_statement;
ROLLBACK;
SQL
)
env -u MYSQL_PWD MYSQL_TEST_LOGIN_FILE=/dev/null mysql --no-defaults \
  --host=fcaj-poc-mysql.cxywse4k25mo.ap-southeast-1.rds.amazonaws.com \
  --port=3306 --user=fcaj_admin --password \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/etc/seal/rds-ca-bundle.pem \
  --connect-timeout=10 --batch --raw --execute "$sql"
