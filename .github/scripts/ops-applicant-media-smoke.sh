#!/usr/bin/env bash
set -eu

YT_HI_ID=opsyt26082401
IG_HI_ID=opsig26082401
YT_ACCOUNT_ID=UCllYMBRvVu_Z2URy7LeevVA
IG_ACCOUNT_ID=kiu_design_
DISABLED_PASSWORD_HASH='$2y$12$5oHjejptGhqIEIfKruYH0uY6.bZymkUiki3dP917a9cGmgKhLUQ.a'

cd /srv/hiselectors
API_CONTAINER="$(docker compose ps -q api)"
test -n "$API_CONTAINER"

container_env() {
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$API_CONTAINER" |
    sed -n "s/^$1=//p" |
    head -n 1
}

DB_HOST="$(container_env DB_HOST)"
DB_PORT="$(container_env DB_PORT)"
DB_NAME="$(container_env DB_NAME)"
DB_USERNAME="$(container_env DB_USERNAME)"
DB_PASSWORD="$(container_env DB_PASSWORD)"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-hiselectors}"
DB_USERNAME="${DB_USERNAME:-root}"
test -n "$DB_HOST"

export MYSQL_PWD="$DB_PASSWORD"
mysql_query() {
  docker run --rm --network "container:$API_CONTAINER" -e MYSQL_PWD mysql:8.4 \
    mysql --connect-timeout=10 --default-character-set=utf8mb4 \
    -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" "$DB_NAME" -Nse "$1"
}

ACTIVE_COUNT="$(mysql_query "SELECT COUNT(*) FROM generation WHERE status = 'ACTIVE' AND start_date <= NOW(6) AND end_date >= NOW(6)")"
test "$ACTIVE_COUNT" = "1"
GENERATION_ID="$(mysql_query "SELECT generation_id FROM generation WHERE status = 'ACTIVE' AND start_date <= NOW(6) AND end_date >= NOW(6) LIMIT 1")"
test -n "$GENERATION_ID"
test "$(mysql_query "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'application' AND column_name IN ('analysis_status', 'analysis_retry_count', 'analyzed_at', 'analysis_error')")" = "4"
test "$(mysql_query "SELECT COUNT(*) FROM application WHERE sns_code = 'YOUTUBE' AND sns_account_id = '$YT_ACCOUNT_ID'")" -gt 0
test "$(mysql_query "SELECT COUNT(*) FROM application WHERE sns_code = 'INSTAGRAM' AND sns_account_id = '$IG_ACCOUNT_ID'")" -gt 0
BACKLOG_COUNT="$(mysql_query "SELECT COUNT(*) FROM application WHERE media_collection_status IN ('PENDING', 'FAILED') AND media_collection_retry_count < 3")"
test "$BACKLOG_COUNT" -le 18
IG_SNAPSHOT_COUNT="$(mysql_query "SELECT COUNT(*) FROM application_media WHERE application_id = (SELECT application_id FROM application WHERE sns_code = 'INSTAGRAM' AND sns_account_id = '$IG_ACCOUNT_ID' AND media_collection_status = 'DONE' ORDER BY application_id DESC LIMIT 1)")"
test "$IG_SNAPSHOT_COUNT" -gt 0
test "$IG_SNAPSHOT_COUNT" -le 100
echo "preflight backlog=$BACKLOG_COUNT instagram_snapshot=$IG_SNAPSHOT_COUNT"

mysql_query "START TRANSACTION;
  INSERT INTO users (hi_id, hi_password, name, alimtalk, created_at, updated_at)
  SELECT '$YT_HI_ID', '$DISABLED_PASSWORD_HASH', '[OPS TEST] YouTube 수집 검증', 'N', NOW(6), NOW(6)
  WHERE NOT EXISTS (SELECT 1 FROM users WHERE hi_id = '$YT_HI_ID');

  INSERT INTO users (hi_id, hi_password, name, alimtalk, created_at, updated_at)
  SELECT '$IG_HI_ID', '$DISABLED_PASSWORD_HASH', '[OPS TEST] Instagram 수집 검증', 'N', NOW(6), NOW(6)
  WHERE NOT EXISTS (SELECT 1 FROM users WHERE hi_id = '$IG_HI_ID');

  UPDATE users
  SET hi_password = '$DISABLED_PASSWORD_HASH',
    name = CASE hi_id
      WHEN '$YT_HI_ID' THEN '[OPS TEST] YouTube 수집 검증'
      WHEN '$IG_HI_ID' THEN '[OPS TEST] Instagram 수집 검증'
    END,
    updated_at = NOW(6)
  WHERE hi_id IN ('$YT_HI_ID', '$IG_HI_ID');

  SET @youtube_user_id = (SELECT user_id FROM users WHERE hi_id = '$YT_HI_ID' LIMIT 1);
  SET @instagram_user_id = (SELECT user_id FROM users WHERE hi_id = '$IG_HI_ID' LIMIT 1);

  INSERT INTO application
    (user_id, generation_id, sns_code, sns_account_id, profile_url,
     follower_count, content_count, last_content_at, engagement_rate,
     alarm_yn, policy_agreed_at, status,
     media_collection_status, media_collection_retry_count, media_collected_at, media_collection_error,
     analysis_status, analysis_retry_count, analyzed_at, analysis_error,
     created_at, updated_at)
  SELECT @youtube_user_id, $GENERATION_ID, 'YOUTUBE', '$YT_ACCOUNT_ID',
         'https://www.youtube.com/channel/$YT_ACCOUNT_ID',
         (SELECT follower_count FROM application WHERE sns_code = 'YOUTUBE' AND sns_account_id = '$YT_ACCOUNT_ID' ORDER BY application_id DESC LIMIT 1),
         (SELECT content_count FROM application WHERE sns_code = 'YOUTUBE' AND sns_account_id = '$YT_ACCOUNT_ID' ORDER BY application_id DESC LIMIT 1),
         NULL, NULL, TRUE, NOW(6), 'PENDING', 'PENDING', 0, NULL, NULL,
         'FAILED', 3, NULL, '[OPS TEST] media-only smoke; analysis intentionally skipped', NOW(6), NOW(6)
  WHERE NOT EXISTS (
    SELECT 1 FROM application
    WHERE user_id = @youtube_user_id AND generation_id = $GENERATION_ID
  );

  INSERT INTO application
    (user_id, generation_id, sns_code, sns_account_id, profile_url,
     follower_count, content_count, last_content_at, engagement_rate,
     alarm_yn, policy_agreed_at, status,
     media_collection_status, media_collection_retry_count, media_collected_at, media_collection_error,
     analysis_status, analysis_retry_count, analyzed_at, analysis_error,
     created_at, updated_at)
  SELECT @instagram_user_id, $GENERATION_ID, 'INSTAGRAM', '$IG_ACCOUNT_ID',
         'https://www.instagram.com/$IG_ACCOUNT_ID/',
         (SELECT follower_count FROM application WHERE sns_code = 'INSTAGRAM' AND sns_account_id = '$IG_ACCOUNT_ID' ORDER BY application_id DESC LIMIT 1),
         (SELECT content_count FROM application WHERE sns_code = 'INSTAGRAM' AND sns_account_id = '$IG_ACCOUNT_ID' ORDER BY application_id DESC LIMIT 1),
         NULL, NULL, TRUE, NOW(6), 'PENDING', 'PENDING', 0, NULL, NULL,
         'FAILED', 3, NULL, '[OPS TEST] media-only smoke; analysis intentionally skipped', NOW(6), NOW(6)
  WHERE NOT EXISTS (
    SELECT 1 FROM application
    WHERE user_id = @instagram_user_id AND generation_id = $GENERATION_ID
  );

  UPDATE application a
  JOIN users u ON u.user_id = a.user_id
  SET a.status = 'PENDING',
      a.media_collection_status = 'PENDING',
      a.media_collection_retry_count = 0,
      a.media_collected_at = NULL,
      a.media_collection_error = NULL,
      a.engagement_rate = NULL,
      a.analysis_status = 'FAILED',
      a.analysis_retry_count = 3,
      a.analyzed_at = NULL,
      a.analysis_error = '[OPS TEST] media-only smoke; analysis intentionally skipped',
      a.updated_at = NOW(6)
  WHERE u.hi_id IN ('$YT_HI_ID', '$IG_HI_ID')
    AND a.generation_id = $GENERATION_ID;
  COMMIT;"

test "$(mysql_query "SELECT COUNT(*) FROM users WHERE hi_id IN ('$YT_HI_ID', '$IG_HI_ID')")" = "2"
test "$(mysql_query "SELECT COUNT(*) FROM application a JOIN users u ON u.user_id = a.user_id WHERE u.hi_id IN ('$YT_HI_ID', '$IG_HI_ID') AND a.generation_id = $GENERATION_ID")" = "2"
echo "seeded generation=$GENERATION_ID"

ATTEMPT=0
while [ "$ATTEMPT" -lt 120 ]; do
  ATTEMPT=$((ATTEMPT + 1))
  DONE_COUNT="$(mysql_query "SELECT COUNT(*) FROM application a JOIN users u ON u.user_id = a.user_id WHERE u.hi_id IN ('$YT_HI_ID', '$IG_HI_ID') AND a.generation_id = $GENERATION_ID AND a.media_collection_status = 'DONE'")"
  echo "poll=$ATTEMPT done=$DONE_COUNT/2"
  if [ "$DONE_COUNT" = "2" ]; then
    break
  fi
  sleep 10
done

mysql_query "SELECT CONCAT(
    u.hi_id,
    '|application=', a.application_id,
    '|sns=', a.sns_code,
    '|account=', a.sns_account_id,
    '|status=', a.media_collection_status,
    '|retry=', a.media_collection_retry_count,
    '|media=', COUNT(am.application_media_id),
    '|contents=', COUNT(DISTINCT am.sns_content_id),
    '|collected=', COALESCE(DATE_FORMAT(a.media_collected_at, '%Y-%m-%dT%H:%i:%s'), '-'),
    '|error=', LEFT(COALESCE(a.media_collection_error, ''), 160)
  )
  FROM application a
  JOIN users u ON u.user_id = a.user_id
  LEFT JOIN application_media am ON am.application_id = a.application_id
  WHERE u.hi_id IN ('$YT_HI_ID', '$IG_HI_ID')
    AND a.generation_id = $GENERATION_ID
  GROUP BY u.hi_id, a.application_id, a.sns_code, a.sns_account_id,
           a.media_collection_status, a.media_collection_retry_count,
           a.media_collected_at, a.media_collection_error
  ORDER BY u.hi_id"

test "$(mysql_query "SELECT COUNT(*) FROM application a JOIN users u ON u.user_id = a.user_id WHERE u.hi_id IN ('$YT_HI_ID', '$IG_HI_ID') AND a.generation_id = $GENERATION_ID AND a.media_collection_status = 'DONE'")" = "2"
test "$(mysql_query "SELECT COUNT(*) FROM application a JOIN users u ON u.user_id = a.user_id JOIN application_media am ON am.application_id = a.application_id WHERE u.hi_id = '$YT_HI_ID' AND a.generation_id = $GENERATION_ID")" -gt 0
test "$(mysql_query "SELECT COUNT(*) FROM application a JOIN users u ON u.user_id = a.user_id JOIN application_media am ON am.application_id = a.application_id WHERE u.hi_id = '$IG_HI_ID' AND a.generation_id = $GENERATION_ID")" -gt 0
test "$(mysql_query "SELECT COUNT(*) FROM application a JOIN users u ON u.user_id = a.user_id WHERE u.hi_id = '$YT_HI_ID' AND a.generation_id = $GENERATION_ID AND a.sns_account_id LIKE 'UC%'")" = "1"
test "$(mysql_query "SELECT COUNT(*) FROM application a JOIN users u ON u.user_id = a.user_id WHERE u.hi_id IN ('$YT_HI_ID', '$IG_HI_ID') AND a.generation_id = $GENERATION_ID AND a.analysis_status = 'FAILED' AND a.analysis_retry_count = 3")" = "2"
echo "ops-applicant-media-smoke=verified"
