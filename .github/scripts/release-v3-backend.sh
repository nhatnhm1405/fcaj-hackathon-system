#!/usr/bin/env bash
# Records NEW_RELEASE in Parameter Store and runs the fixed release document on the API hosts, one at a time.
# When a host fails, the previous release is recorded and released again. Database schema is never changed here.
set -euo pipefail
: "${RELEASE_PARAMETER:?}" "${NEW_RELEASE:?}"
[[ "$NEW_RELEASE" =~ ^[0-9]{12}\.dkr\.ecr\.ap-southeast-1\.amazonaws\.com/fcaj-poc-backend@sha256:[0-9a-f]{64}\ [0-9a-f]{40}$ ]]
POLL_SECONDS="${POLL_SECONDS:-15}"

record() { aws ssm put-parameter --name "$RELEASE_PARAMETER" --type String --overwrite --value "$1" --output text >/dev/null; }

run_release() {
  local id status targets attempt
  id="$(aws ssm send-command --document-name fcaj-v3-release --targets Key=tag:SealRole,Values=api \
    --max-concurrency 1 --max-errors 0 --timeout-seconds 600 --query Command.CommandId --output text)"
  for attempt in $(seq 1 80); do
    sleep "$POLL_SECONDS"
    read -r status targets <<<"$(aws ssm list-commands --command-id "$id" --query 'Commands[0].[Status,TargetCount]' --output text)"
    case "$status" in
      Pending|InProgress|Cancelling) ;;
      Success)
        if [ "${targets:-0}" = 0 ]; then
          echo '::warning::No API host is running. The new release starts with the next host.'
        else
          echo "RELEASED on $targets host(s)"
        fi
        return 0 ;;
      *) echo "Command $id ended with status $status"; return 1 ;;
    esac
  done
  echo "Command $id did not finish in time"
  return 1
}

previous="$(aws ssm get-parameter --name "$RELEASE_PARAMETER" --query Parameter.Value --output text)"
record "$NEW_RELEASE"
if run_release; then exit 0; fi

echo '::error::Release failed; returning to the previous release.'
record "$previous"
if run_release; then echo 'ROLLED_BACK'; else echo '::error::Rollback failed too; check the hosts now.'; fi
exit 1
