#!/usr/bin/env bash
# Run on the deploy host from the exact candidate checkout. No defaults to the legacy compose DB.
set -euo pipefail
: "${CANDIDATE_REVISION:?Exact candidate SHA required}"
: "${DEPLOY_COMPOSE_FILE:?BE2 production compose file required}"
: "${DEPLOY_MIGRATION_ENV_FILE:?Private migration env file required}"
: "${DEPLOY_DB_CATALOG:?Exact target catalog required}"
: "${DEPLOY_RUNTIME_JDBC_URL:?Exact runtime target required}"
case "${DEPLOY_DB_MODE:-verify-only}" in
  verify-only|initialize-empty) ;;
  *) echo "Unsupported DEPLOY_DB_MODE; use verify-only or initialize-empty." >&2; exit 1 ;;
esac
db_mount=()
if [ -n "${DEPLOY_DB_TRUSTSTORE_FILE:-}" ]; then
  test -f "$DEPLOY_DB_TRUSTSTORE_FILE"
  db_mount=(--mount "type=bind,src=$DEPLOY_DB_TRUSTSTORE_FILE,dst=/run/secrets/rds-truststore.p12,readonly")
fi
test "$(git rev-parse HEAD)" = "$CANDIDATE_REVISION"
git diff --quiet
git diff --cached --quiet
test -z "$(git ls-files --others --exclude-standard back-end/src/fcaj-api)"
export CANDIDATE_BACKEND_IMAGE="seal-backend-candidate:$CANDIDATE_REVISION"
docker build -t "$CANDIDATE_BACKEND_IMAGE" back-end/src/fcaj-api
# Production compose must select this candidate and its intended runtime target explicitly.
docker compose -f "$DEPLOY_COMPOSE_FILE" config --format json | python3 -c '
import json,os,sys
c=json.load(sys.stdin)["services"]["seal-backend"]
e=c.get("environment",{})
assert c.get("image")==os.environ["CANDIDATE_BACKEND_IMAGE"], "Compose must select candidate image"
assert "aws" in e.get("SPRING_PROFILES_ACTIVE", "").split(","), "AWS profile required"
assert e.get("SPRING_DATASOURCE_URL")==os.environ["DEPLOY_RUNTIME_JDBC_URL"], "Runtime target mismatch"
'
# Explicit first-deployment mode. A known V3 database is a no-op; any other nonempty
# schema is rejected. Operator-led upgrades/baselines remain separate from deploy.
if [ "${DEPLOY_DB_MODE:-verify-only}" = initialize-empty ]; then
  docker run --rm --env-file "$DEPLOY_MIGRATION_ENV_FILE" -e "BE1_EXPECTED_RUNTIME_JDBC_URL=$DEPLOY_RUNTIME_JDBC_URL" "${db_mount[@]}" --entrypoint java \
    "$CANDIDATE_BACKEND_IMAGE" -Dloader.main=com.fcaj.hackathon.db.DatabaseMigrationMain \
    -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher initialize-empty "$DEPLOY_DB_CATALOG"
fi
# This CLI is in the candidate fat jar; PropertiesLauncher selects the operator main, not Spring.
docker run --rm --env-file "$DEPLOY_MIGRATION_ENV_FILE" -e "BE1_EXPECTED_RUNTIME_JDBC_URL=$DEPLOY_RUNTIME_JDBC_URL" "${db_mount[@]}" --entrypoint java \
  "$CANDIDATE_BACKEND_IMAGE" -Dloader.main=com.fcaj.hackathon.db.DatabaseMigrationMain \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher verify-deploy "$DEPLOY_DB_CATALOG"
# No service replacement, stop, remove or compose up occurs before the gate above succeeds.
docker compose -f "$DEPLOY_COMPOSE_FILE" up -d --no-build
docker compose -f "$DEPLOY_COMPOSE_FILE" ps
