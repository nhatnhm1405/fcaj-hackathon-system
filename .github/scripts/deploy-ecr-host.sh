#!/usr/bin/env bash
# Run only through the scoped SSM deploy role. Never print expanded private env.
set -euo pipefail
[[ "$1" =~ ^582129522297\.dkr\.ecr\.ap-southeast-1\.amazonaws\.com/fcaj-poc-backend@sha256:[0-9a-f]{64}$ ]]
[[ "$2" =~ ^[0-9a-f]{40}$ ]]
test -f /etc/seal/deploy-settings.sh
set -a
. /etc/seal/deploy-settings.sh
set +a
export DEPLOY_IMAGE="$1" CANDIDATE_REVISION="$2"
# Initialization remains an explicit operator task, never a CI side effect.
test "${DEPLOY_DB_MODE:-verify-only}" = verify-only
aws ecr get-login-password --region ap-southeast-1 |
  docker login --username AWS --password-stdin 582129522297.dkr.ecr.ap-southeast-1.amazonaws.com >/dev/null
docker pull "$DEPLOY_IMAGE" >/dev/null
release_dir="$(mktemp -d /tmp/seal-release.XXXXXXXX)"
extract_container="$(docker create "$DEPLOY_IMAGE")"
cleanup() {
  docker rm "$extract_container" >/dev/null 2>&1 || true
  # Only files created in the mktemp directory above are removed.
  rm -f -- "$release_dir/deploy-verified.sh"
  rmdir -- "$release_dir" || true
}
trap cleanup EXIT
docker cp "$extract_container:/opt/seal/deploy-verified.sh" "$release_dir/deploy-verified.sh"
bash "$release_dir/deploy-verified.sh"
container_id="$(docker compose -f "$DEPLOY_COMPOSE_FILE" ps -q seal-backend)"
test -n "$container_id"
test "$(docker inspect --format '{{.Config.Image}}' "$container_id")" = "$DEPLOY_IMAGE"
test "$(docker inspect --format '{{.Image}}' "$container_id")" = "$(docker image inspect --format '{{.Id}}' "$DEPLOY_IMAGE")"
healthy=false
for _ in $(seq 1 60); do
  if curl --fail --silent http://127.0.0.1:8080/health/ready >/dev/null; then healthy=true; break; fi
  sleep 2
done
test "$healthy" = true
curl --fail --silent http://127.0.0.1:8080/health/version |
  python3 -c 'import json,os,sys; v=json.load(sys.stdin); assert v["revision"]==os.environ["CANDIDATE_REVISION"], "Runtime revision mismatch"'
install -d -m 700 /var/lib/seal/releases
python3 - <<'PY'
import datetime,json,os
record={"revision":os.environ["CANDIDATE_REVISION"],"image":os.environ["DEPLOY_IMAGE"],"verifiedAt":datetime.datetime.now(datetime.timezone.utc).isoformat()}
path="/var/lib/seal/releases/"+record["revision"]+"-"+record["image"].split(":")[-1]+".json"
fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,"w") as stream: json.dump(record,stream)
PY
