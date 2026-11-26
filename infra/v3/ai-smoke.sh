#!/usr/bin/env bash
# AI job smoke test on the demo data of the restored dump. Run on an API host: sudo bash ai-smoke.sh on|off
#   on  : submission 1 points to a small public sample repository and judging of round 1 opens for 2 hours
#   off : both rows go back to the values in the dump
# Uses the backend's own database login. Changes exactly two rows; refuses when they are not the demo rows.
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo 'STOP: run with sudo' >&2; exit 1; }
value() { sed -n "s/^$1=//p" /etc/seal/runtime.env; }
host="$(value SPRING_DATASOURCE_URL | sed -n 's#^jdbc:mysql://\([^:/]*\):3306/.*#\1#p')"
MYSQL_PWD="$(value SPRING_DATASOURCE_PASSWORD)"; export MYSQL_PWD
db() { mysql -h "$host" -u fcaj_v3_runtime --ssl-mode=VERIFY_IDENTITY --ssl-ca=/etc/seal/rds-ca-bundle.pem seal_hackathon "$@"; }
SAMPLE=https://github.com/expressjs/cors
ORIGINAL=https://github.com/seal-demo/arsenal
known="$(db -N -e "SELECT COUNT(*) FROM submission s JOIN roundtimer t ON t.round_id = s.round_id
  WHERE s.submission_id = 1 AND s.round_id = 1 AND s.repo_url IN ('$SAMPLE', '$ORIGINAL') AND t.timer_id = 2 AND t.phase = 'JUDGING'")"
[ "$known" = 1 ] || { echo 'STOP: demo rows not found; nothing was changed' >&2; exit 1; }

case "${1:-}" in
  on)
    db -e "START TRANSACTION;
      UPDATE submission SET repo_url = '$SAMPLE' WHERE submission_id = 1;
      UPDATE roundtimer SET status = 'RUNNING', ends_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 2 HOUR) WHERE timer_id = 2;
      COMMIT;"
    echo 'SMOKE_ON for 2 hours. Log in on the web as one of these judges:'
    db -N -e "SELECT DISTINCT u.email FROM judgeassignment ja JOIN \`user\` u ON u.user_id = ja.judge_user_id
      JOIN submission s ON s.submission_id = 1 AND s.round_id = ja.round_id
      JOIN teamevententry e ON e.team_id = s.team_id AND e.event_id = 1 AND (ja.track_id IS NULL OR ja.track_id = e.track_id)
      WHERE ja.is_active = 1 AND u.is_active = 1"
    ;;
  off)
    db -e "START TRANSACTION;
      UPDATE submission SET repo_url = '$ORIGINAL' WHERE submission_id = 1;
      UPDATE roundtimer SET status = 'EXPIRED', ends_at = '2026-07-20 03:24:45.782765' WHERE timer_id = 2;
      COMMIT;"
    echo 'SMOKE_OFF original values restored'
    ;;
  *) echo 'Usage: sudo bash ai-smoke.sh on|off' >&2; exit 2 ;;
esac
