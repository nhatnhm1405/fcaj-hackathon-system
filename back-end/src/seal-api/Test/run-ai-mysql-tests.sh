#!/usr/bin/env bash
# Disposable CI runner / isolated local Docker instance; no existing DB or AWS access.
set -euo pipefail
cd "$(dirname "$0")/.."
sh ./mvnw -B -DskipTests package
lab_name="fcaj-ai-lab-$(openssl rand -hex 6)"
export MYSQL_ROOT_PASSWORD
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then echo "::add-mask::$MYSQL_ROOT_PASSWORD"; fi
cleanup() { docker stop --time 15 "$lab_name" >/dev/null 2>&1 || true; }
docker run --detach --name "$lab_name" --label fcaj.purpose=ai-integration-lab \
  --env MYSQL_ROOT_PASSWORD --env MYSQL_ROOT_HOST=% --env MYSQL_DATABASE=fcaj_ai_integration_lab \
  --publish 127.0.0.1::3306 \
  mysql@sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69 \
  --lower-case-table-names=1 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
trap cleanup EXIT
ready=false
for _ in $(seq 1 60); do
  if docker exec "$lab_name" mysqladmin ping -h 127.0.0.1 --silent >/dev/null 2>&1; then ready=true; break; fi
  sleep 2
done
if [[ "$ready" != "true" ]]; then echo "MySQL lab not ready" >&2; exit 1; fi
binding="$(docker port "$lab_name" 3306/tcp)"
if [[ ! "$binding" =~ ^127\.0\.0\.1:([0-9]+)$ ]]; then echo "Unexpected private binding" >&2; exit 1; fi
export FCAJ_AI_TEST_JDBC_URL="jdbc:mysql://127.0.0.1:${BASH_REMATCH[1]}/fcaj_ai_integration_lab?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=5000&socketTimeout=30000"
sh ./mvnw -B -Dspring.profiles.active=test -Dlogging.level.root=WARN -Dsurefire.runOrder=alphabetical -Dtest=AiJobIntegrationTest,FileReferenceConcurrencyTest,FreshDatabaseDeploymentTest test
