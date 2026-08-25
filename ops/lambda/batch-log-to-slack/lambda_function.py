import base64
from datetime import datetime
import gzip
import html
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
TASK_LABELS = {
    "CREATOR_SYNC": "크리에이터 수집",
    "CONTENT_SYNC": "콘텐츠 수집",
    "APPLICATION_REPORT_GENERATION": "지원자 리포트 생성",
    "CONTENT_REPORT_GENERATION": "콘텐츠 검수 리포트 생성",
    "SETTLEMENT_CALCULATION": "정산 계산",
    "KAKAO_MESSAGE_SEND": "카카오 메시지 발송",
    "PROPOSAL_EMAIL_SEND": "제안 이메일 발송",
}
TRIGGER_LABELS = {
    "SCHEDULED": "자동 실행",
    "ADMIN_TRIGGERED": "관리자 실행",
}
STATUS_TITLES = {
    "PARTIAL_FAILED": "⚠️ {task} 일부 실패",
    "FAILED": "🚨 {task} 실패",
    "STALE": "⏱️ {task} 비정상 종료",
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
                    _task_run_notification(batch_event),
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
    return event["batch"] == "task-run" and event["status"] in {
        "PARTIAL_FAILED",
        "FAILED",
        "STALE",
    }


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


def _task_run_notification(event):
    details = event.get("details", {})
    task = TASK_LABELS.get(details.get("taskType"), "알 수 없는 작업")
    trigger = TRIGGER_LABELS.get(details.get("triggerType"), "알 수 없는 실행")
    description = [
        "실행 방식: " + trigger,
    ]
    counts = _format_counts(event.get("counts"))
    if counts:
        description.append("처리 결과: " + counts)
    description.extend(
        [
            "실패 원인: " + html.escape(event["error"]["message"], quote=False),
            "실행 시간: " + _format_duration(event["durationMs"]),
            "실행 ID: " + event["runId"],
        ]
    )
    return {
        "version": "1.0",
        "source": "custom",
        "content": {
            "textType": "client-markdown",
            "title": STATUS_TITLES[event["status"]].format(task=task),
            "description": "\n".join(description),
        },
    }


def _format_counts(counts):
    if not counts or not any(
        counts.get(key, 0) > 0
        for key in ("total", "processed", "succeeded", "failed", "skipped")
    ):
        return None
    first_label = "전체" if "total" in counts else "처리"
    first_value = counts.get("total", counts.get("processed", 0))
    return (
        f"{first_label} {first_value}건 / "
        f"성공 {counts.get('succeeded', 0)}건 / "
        f"실패 {counts.get('failed', 0)}건"
    )


def _format_duration(duration_ms):
    seconds = duration_ms // 1000
    if seconds < 60:
        return str(seconds) + "초"
    minutes, seconds = divmod(seconds, 60)
    if minutes < 60:
        return str(minutes) + "분 " + str(seconds) + "초"
    hours, minutes = divmod(minutes, 60)
    return str(hours) + "시간 " + str(minutes) + "분 " + str(seconds) + "초"


def _sns_client():
    import boto3

    return boto3.client("sns")
