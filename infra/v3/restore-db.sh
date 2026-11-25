#!/usr/bin/env bash
# One-time load of the code-first dump into the EMPTY v3 database. Run on an API host: sudo bash restore-db.sh
# Asks for the RDS master password (Secrets Manager); never prints or stores it.
# Refuses to run when any table or application login already exists, because the dump contains DROP TABLE.
set -euo pipefail
umask 077
export HOME=/root PATH="$PATH:/snap/bin"
SHA=ab6279c0ad77be4405ca1505c1da8b7db2a1a7c6b9e052db5b672790e1f23802
EXPECTED_TABLES=31
DB=seal_hackathon
FILE=/etc/seal/seal_hackathon.bak
stop() { echo "STOP: $*" >&2; exit 1; }
[ "$(id -u)" = 0 ] || stop 'run with sudo'

echo '1/5 Waiting for first-boot setup to finish'
cloud-init status --wait >/dev/null 2>&1 || true
[ -x /usr/local/bin/seal-release ] || stop 'first-boot setup did not complete; check /var/log/seal-bootstrap.log'
account="$(aws sts get-caller-identity --query Account --output text)"
host="$(sed -n 's#^SPRING_DATASOURCE_URL=jdbc:mysql://\([^:/]*\):3306/.*#\1#p' /etc/seal/runtime.env)"
runtime_pw="$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' /etc/seal/runtime.env)"
migration_pw="$(sed -n 's/^BE1_MIGRATION_PASSWORD=//p' /etc/seal/migration.env)"
[ -n "$host" ] || stop 'database host not found in runtime.env'
[[ "$runtime_pw" =~ ^[A-Za-z0-9]{32,}$ && "$migration_pw" =~ ^[A-Za-z0-9]{32,}$ ]] || stop 'unexpected password format'

echo '2/5 Downloading and checking the dump'
aws s3 cp --only-show-errors "s3://fcaj-poc-data-$account-ap-southeast-1/poc/restore/seal_hackathon.bak" "$FILE"
echo "$SHA  $FILE" | sha256sum --check --status || stop 'checksum mismatch'

IFS= read -rsp '3/5 Paste the fcaj_admin password, then press Enter (nothing is shown): ' MYSQL_PWD </dev/tty
echo
export MYSQL_PWD
db() { mysql -h "$host" -u fcaj_admin --ssl-mode=VERIFY_IDENTITY --ssl-ca=/etc/seal/rds-ca-bundle.pem --default-character-set=utf8mb4 "$@"; }
tables() { db -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$DB'"; }
settings="$(db -N -e 'SELECT CONCAT(@@lower_case_table_names, @@partial_revokes)')" || stop 'cannot log in to the database'
[ "$settings" = 10 ] || stop "unexpected server settings $settings"
[ "$(tables)" = 0 ] || stop 'the database is not empty; nothing was changed'
[ "$(db -N -e "SELECT COUNT(*) FROM mysql.user WHERE user LIKE 'fcaj\\_v3\\_%'")" = 0 ] || stop 'application logins already exist; nothing was changed'

echo '4/5 Loading the dump and creating the two application logins'
db "$DB" < "$FILE"
[ "$(tables)" = "$EXPECTED_TABLES" ] || stop "expected $EXPECTED_TABLES tables, found $(tables)"
db <<SQL
CREATE USER 'fcaj_v3_runtime'@'10.20.%' IDENTIFIED BY '$runtime_pw' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE ON \`seal\\_hackathon\`.* TO 'fcaj_v3_runtime'@'10.20.%';
CREATE USER 'fcaj_v3_migration'@'10.20.%' IDENTIFIED BY '$migration_pw' REQUIRE SSL;
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON \`seal\\_hackathon\`.* TO 'fcaj_v3_migration'@'10.20.%';
SQL
unset MYSQL_PWD
echo "RESTORE_OK $EXPECTED_TABLES tables"

echo '5/5 Applying migrations and starting the backend (a few minutes)'
DEPLOY_DB_MODE=adopt-restored SEAL_BACKUP_FILE="$FILE" SEAL_BACKUP_SHA256="$SHA" /usr/local/bin/seal-release
