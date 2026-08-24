import base64
from datetime import datetime
import gzip
import json
import math
import os
import re
from uuid import UUID


PREFIX = "BATCH_EVENT "
TERMINAL_STATUSES = {
    "SUCCEEDED",
    "PARTIAL_FAILURE",
    "PARTIAL_FAILED",
    "FAILED",
    "STALE",
}
ALLOWED_STATUSES = TERMINAL_STATUSES | {"STARTED", "SKIPPED"}
CONTENT_SYNC_STATUSES = {"STARTED", "SUCCEEDED", "PARTIAL_FAILURE", "FAILED", "SKIPPED"}
TASK_RUN_STATUSES = {"SUCCEEDED", "PARTIAL_FAILED", "FAILED", "STALE"}
TASK_RUN_ONLY_STATUSES = {"PARTIAL_FAILED", "STALE"}
BATCH_NAME = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
MARKER = re.compile(r"(?<!\S)" + re.escape(PREFIX))
METADATA_KEY = re.compile(r"^[a-z][a-zA-Z0-9]*(?:-[a-z0-9]+)*$")
TITLES = {
    "SUCCEEDED": "✅ Batch succeeded",
    "PARTIAL_FAILURE": "⚠️ Batch partially failed",
    "PARTIAL_FAILED": "⚠️ Batch partially failed",
    "FAILED": "🚨 Batch failed",
    "STALE": "🚨 Batch stale",
}


def lambda_handler(event, _context, sns_client=None):
    payload = json.loads(gzip.decompress(base64.b64decode(event["awslogs"]["data"])))
    if payload.get("messageType") == "CONTROL_MESSAGE":
        return {"published": 0}

    published = 0
    for log_event in payload.get("logEvents", []):
        try:
            batch_event = _parse(log_event.get("message"))
        except (AttributeError, KeyError, TypeError, ValueError, json.JSONDecodeError):
            continue
        if batch_event and _should_publish(batch_event):
            topic_arn = os.environ.get("SNS_TOPIC_ARN")
            if not topic_arn:
                raise RuntimeError("SNS_TOPIC_ARN is required")
            client = sns_client or _sns_client()
            client.publish(
                TopicArn=topic_arn,
                Message=json.dumps(
                    _notification(batch_event),
                    ensure_ascii=False,
                    separators=(",", ":"),
                ),
            )
            published += 1
    return {"published": published}


def _parse(message):
    if not isinstance(message, str):
        return None
    try:
        docker_entry = json.loads(message)
        if isinstance(docker_entry, dict) and isinstance(docker_entry.get("log"), str):
            message = docker_entry["log"].rstrip("\r\n")
    except json.JSONDecodeError:
        pass
    marker = MARKER.search(message)
    if marker is None:
        return None

    event = json.loads(message[marker.end() :])
    if not _valid(event):
        return None
    return event


def _valid(event):
    if not isinstance(event, dict):
        return False
    if type(event.get("schemaVersion")) is not int or event["schemaVersion"] != 1:
        return False
    if event.get("event") != "BATCH_RUN":
        return False
    if not isinstance(event.get("batch"), str) or not BATCH_NAME.fullmatch(event["batch"]):
        return False
    if not _uuid(event.get("runId")) or event.get("status") not in ALLOWED_STATUSES:
        return False
    if not _valid_batch_status(event):
        return False
    if not _offset_timestamp(event.get("timestamp")):
        return False
    if not _valid_optional_fields(event):
        return False
    if event["batch"] == "task-run" and not _valid_task_run_details(event):
        return False

    duration = event.get("durationMs")
    if event["status"] == "STARTED":
        return "durationMs" not in event
    if event["status"] == "SKIPPED" and not _bounded_string(event.get("reason")):
        return False
    if event["status"] in {"PARTIAL_FAILED", "FAILED", "STALE"} and "error" not in event:
        return False
    return type(duration) is int and duration >= 0


def _valid_batch_status(event):
    if event["batch"] == "content-sync":
        return event["status"] in CONTENT_SYNC_STATUSES
    if event["batch"] == "task-run":
        return event["status"] in TASK_RUN_STATUSES
    return event["status"] not in TASK_RUN_ONLY_STATUSES


def _valid_task_run_details(event):
    details = event.get("details")
    return isinstance(details, dict) and all(
        _nonblank_string(details.get(key)) for key in ("taskType", "triggerType")
    )


def _valid_optional_fields(event):
    if "counts" in event:
        counts = event["counts"]
        if not isinstance(counts, dict) or not all(
            _metadata_key(key) and type(value) is int and value >= 0
            for key, value in counts.items()
        ):
            return False
    if "details" in event:
        details = event["details"]
        if not isinstance(details, dict) or not all(
            _metadata_key(key) and _safe_scalar(value)
            for key, value in details.items()
        ):
            return False
    if "reason" in event and not _bounded_string(event["reason"]):
        return False
    if "error" in event:
        error = event["error"]
        if (
            not isinstance(error, dict)
            or set(error) != {"type", "message"}
            or not _bounded_string(error["type"])
            or not _bounded_string(error["message"])
        ):
            return False
    return True


def _metadata_key(value):
    return isinstance(value, str) and len(value) <= 64 and METADATA_KEY.fullmatch(value)


def _bounded_string(value):
    return isinstance(value, str) and 0 < len(value) <= 500


def _nonblank_string(value):
    return _bounded_string(value) and bool(value.strip())


def _safe_scalar(value):
    if isinstance(value, str):
        return len(value) <= 500
    if isinstance(value, bool):
        return True
    if type(value) is int:
        return True
    return type(value) is float and math.isfinite(value)


def _should_publish(event):
    if event["batch"] == "task-run":
        return event["status"] in {"PARTIAL_FAILED", "FAILED", "STALE"}
    return event["status"] in TERMINAL_STATUSES or (
        event["batch"] == "content-sync" and event["status"] == "SKIPPED"
    )


def _uuid(value):
    try:
        return isinstance(value, str) and str(UUID(value)) == value.lower()
    except ValueError:
        return False


def _offset_timestamp(value):
    try:
        if isinstance(value, str) and value.endswith("Z"):
            value = value[:-1] + "+00:00"
        timestamp = datetime.fromisoformat(value)
        return timestamp.tzinfo is not None and timestamp.utcoffset() is not None
    except (TypeError, ValueError):
        return False


def _notification(event):
    if event["batch"] == "content-sync":
        return _content_sync_notification(event)
    if event["batch"] == "task-run":
        return _task_run_notification(event)

    description = [
        "**Status:** " + event["status"],
        "**Run ID:** " + event["runId"],
        "**Duration:** " + str(event["durationMs"]) + " ms",
    ]
    for label, key in (("Counts", "counts"), ("Details", "details"), ("Reason", "reason"), ("Error", "error")):
        if key in event:
            value = event[key]
            if isinstance(value, (dict, list)):
                value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
            description.append("**" + label + ":** `" + str(value) + "`")
    return {
        "version": "1.0",
        "source": "custom",
        "content": {
            "textType": "client-markdown",
            "title": TITLES[event["status"]] + ": " + event["batch"],
            "description": "\n".join(description),
        },
    }


def _task_run_notification(event):
    details = event.get("details", {})
    task_type = details.get("taskType", "UNKNOWN")
    trigger_type = details.get("triggerType", "UNKNOWN")
    error = event["error"]
    title = {
        "PARTIAL_FAILED": "⚠️ TaskRun partially failed",
        "FAILED": "🚨 TaskRun failed",
        "STALE": "🚨 TaskRun stale",
    }[event["status"]]
    description = [
        "**Status:** " + event["status"],
        "**Task type:** " + task_type,
        "**Trigger type:** " + trigger_type,
        "**Run ID:** " + event["runId"],
        "**Duration:** " + str(event["durationMs"]) + " ms",
        "**Error type:** `" + error["type"] + "`",
        "**Error:** " + error["message"],
    ]
    if "counts" in event:
        description.append(
            "**Counts:** `"
            + json.dumps(event["counts"], ensure_ascii=False, separators=(",", ":"))
            + "`"
        )
    return {
        "version": "1.0",
        "source": "custom",
        "content": {
            "textType": "client-markdown",
            "title": title + ": " + task_type,
            "description": "\n".join(description),
        },
    }


def _content_sync_notification(event):
    counts = event.get("counts", {})
    instagram = _platform_row(counts, "instagram")
    youtube = _platform_row(counts, "youtube")
    failed_stage_count = counts.get("failedStageCount", 0)
    total = [
        instagram[index] + youtube[index]
        for index in range(4)
    ]
    total_failed = instagram[4] + youtube[4] + failed_stage_count
    title = {
        "SUCCEEDED": "✅ 콘텐츠 동기화 완료",
        "PARTIAL_FAILURE": "⚠️ 콘텐츠 동기화 부분 실패",
        "FAILED": "🚨 콘텐츠 동기화 실패",
        "SKIPPED": (
            "ℹ️ 콘텐츠 동기화 대상 없음"
            if event.get("reason") == "NO_TARGETS"
            else "ℹ️ 콘텐츠 동기화 실행 건너뜀"
        ),
    }[event["status"]]
    description = [
        "```",
        "플랫폼 | 신규 후보 | 셀렉터스 | 수정 감지 | 버전 저장 | 실패",
        _table_row("Instagram", instagram),
        _table_row("YouTube", youtube),
        _table_row("합계", (*total, total_failed)),
        "```",
        "실행 시간: " + format(event["durationMs"] / 1000, ".1f") + "초",
        "실행 ID: " + event["runId"],
    ]
    if failed_stage_count:
        description.append("플랫폼 미분류 단계 실패: " + str(failed_stage_count))
    if "details" in event:
        description.append(
            "상세: `"
            + json.dumps(event["details"], ensure_ascii=False, separators=(",", ":"))
            + "`"
        )
    if "error" in event:
        description.append("오류: `" + event["error"]["type"] + "`")
    return {
        "version": "1.0",
        "source": "custom",
        "content": {
            "textType": "client-markdown",
            "title": title,
            "description": "\n".join(description),
        },
    }


def _platform_row(counts, prefix):
    return (
        counts.get(prefix + "NewCandidateCount", 0),
        counts.get(prefix + "SelectorsContentCount", 0),
        counts.get(prefix + "ChangedContentCount", 0),
        counts.get(prefix + "SavedVersionCount", 0),
        counts.get(prefix + "FailedCount", 0),
    )


def _table_row(label, values):
    return label + " | " + " | ".join(str(value) for value in values)


def _sns_client():
    import boto3

    return boto3.client("sns")
