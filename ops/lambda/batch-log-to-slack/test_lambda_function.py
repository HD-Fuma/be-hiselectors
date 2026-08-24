import base64
import gzip
import importlib
import json
import os
import sys
import unittest
from unittest.mock import Mock, patch


sys.path.insert(0, os.path.dirname(__file__))
lambda_function = importlib.import_module("lambda_function")


TOPIC_ARN = "arn:aws:sns:ap-northeast-2:167595589232:batch-alerts"


def batch_event(status="SUCCEEDED", **overrides):
    event = {
        "schemaVersion": 1,
        "event": "BATCH_RUN",
        "batch": "content-sync",
        "runId": "9fb63104-feca-4c92-a04c-4ebdc4a0bf6f",
        "status": status,
        "timestamp": "2026-08-22T13:40:00+09:00",
    }
    if status != "STARTED":
        event["durationMs"] = 125
    event.update(overrides)
    return event


def cloudwatch_event(*messages, message_type="DATA_MESSAGE"):
    payload = {
        "messageType": message_type,
        "owner": "167595589232",
        "logGroup": "/verified/application/log-group",
        "logStream": "app",
        "logEvents": [
            {"id": str(index), "timestamp": index, "message": message}
            for index, message in enumerate(messages)
        ],
    }
    return {
        "awslogs": {
            "data": base64.b64encode(
                gzip.compress(json.dumps(payload).encode("utf-8"))
            ).decode("ascii")
        }
    }


def line(event):
    return "BATCH_EVENT " + json.dumps(event, ensure_ascii=False)


def content_sync_counts(**overrides):
    counts = {
        "instagramNewCandidateCount": 14,
        "instagramSelectorsContentCount": 6,
        "instagramChangedContentCount": 2,
        "instagramSavedVersionCount": 8,
        "instagramFailedCount": 0,
        "youtubeNewCandidateCount": 7,
        "youtubeSelectorsContentCount": 3,
        "youtubeChangedContentCount": 1,
        "youtubeSavedVersionCount": 4,
        "youtubeFailedCount": 3,
        "failedStageCount": 0,
    }
    counts.update(overrides)
    return counts


class LambdaHandlerTest(unittest.TestCase):
    def setUp(self):
        self.sns = Mock()
        self.environment = patch.dict(os.environ, {"SNS_TOPIC_ARN": TOPIC_ARN}, clear=True)
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def test_publishes_exact_success_notification(self):
        event = batch_event(counts=content_sync_counts(
            youtubeFailedCount=0,
        ))

        result = lambda_function.lambda_handler(cloudwatch_event(line(event)), None, self.sns)

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once_with(
            TopicArn=TOPIC_ARN,
            Message=json.dumps(
                {
                    "version": "1.0",
                    "source": "custom",
                    "content": {
                        "textType": "client-markdown",
                        "title": "✅ 콘텐츠 동기화 완료",
                        "description": (
                            "```\n"
                            "플랫폼 | 신규 후보 | 셀렉터스 | 수정 감지 | 버전 저장 | 실패\n"
                            "Instagram | 14 | 6 | 2 | 8 | 0\n"
                            "YouTube | 7 | 3 | 1 | 4 | 0\n"
                            "합계 | 21 | 9 | 3 | 12 | 0\n"
                            "```\n"
                            "실행 시간: 0.1초\n"
                            "실행 ID: 9fb63104-feca-4c92-a04c-4ebdc4a0bf6f"
                        ),
                    },
                },
                ensure_ascii=False,
                separators=(",", ":"),
            ),
        )

    def test_unwraps_docker_log_and_preserves_unicode(self):
        event = batch_event(
            "PARTIAL_FAILURE",
            details={"message": "일부 항목 실패"},
        )
        docker_message = json.dumps({"log": line(event) + "\n", "stream": "stdout"})

        lambda_function.lambda_handler(cloudwatch_event(docker_message), None, self.sns)

        message = self.sns.publish.call_args.kwargs["Message"]
        self.assertIn("일부 항목 실패", message)
        self.assertNotIn("\\uc77c", message)
        self.assertEqual(
            "⚠️ 콘텐츠 동기화 부분 실패",
            json.loads(message)["content"]["title"],
        )

    def test_publishes_task_run_partial_failed_with_task_type_and_actual_run_id(self):
        event = batch_event(
            "PARTIAL_FAILED",
            batch="task-run",
            runId="2284fbed-2d99-422f-a18e-e875055fcb38",
            counts={
                "processed": 4,
                "succeeded": 2,
                "failed": 1,
                "skipped": 1,
            },
            details={"taskType": "CONTENT_SYNC", "triggerType": "SCHEDULED"},
            error={
                "type": "TASK_RUN_PARTIAL_FAILED",
                "message": "일부 처리 항목이 실패했습니다.",
            },
        )

        result = lambda_function.lambda_handler(
            cloudwatch_event(line(event)), None, self.sns
        )

        self.assertEqual({"published": 1}, result)
        notification = json.loads(self.sns.publish.call_args.kwargs["Message"])
        self.assertEqual(
            "⚠️ TaskRun partially failed: CONTENT_SYNC",
            notification["content"]["title"],
        )
        self.assertIn(
            "**Run ID:** 2284fbed-2d99-422f-a18e-e875055fcb38",
            notification["content"]["description"],
        )
        self.assertIn(
            "**Task type:** CONTENT_SYNC", notification["content"]["description"]
        )
        self.assertIn(
            "일부 처리 항목이 실패했습니다.",
            notification["content"]["description"],
        )

    def test_publishes_task_run_stale_alert(self):
        event = batch_event(
            "STALE",
            batch="task-run",
            runId="e44282da-2300-49f8-a281-d107f7344f11",
            counts={
                "processed": 0,
                "succeeded": 0,
                "failed": 0,
                "skipped": 0,
            },
            details={"taskType": "CREATOR_SYNC", "triggerType": "SCHEDULED"},
            error={
                "type": "TASK_RUN_STALE",
                "message": "제한 시간 동안 heartbeat가 없어 비정상 종료로 판정했습니다.",
            },
        )

        result = lambda_function.lambda_handler(
            cloudwatch_event(line(event)), None, self.sns
        )

        self.assertEqual({"published": 1}, result)
        notification = json.loads(self.sns.publish.call_args.kwargs["Message"])
        self.assertEqual(
            "🚨 TaskRun stale: CREATOR_SYNC", notification["content"]["title"]
        )
        self.assertIn(
            "**Run ID:** e44282da-2300-49f8-a281-d107f7344f11",
            notification["content"]["description"],
        )
        self.assertIn("TASK_RUN_STALE", notification["content"]["description"])

    def test_suppresses_succeeded_task_run(self):
        event = batch_event(
            "SUCCEEDED",
            batch="task-run",
            details={"taskType": "CONTENT_SYNC", "triggerType": "SCHEDULED"},
        )

        result = lambda_function.lambda_handler(
            cloudwatch_event(line(event)), None, self.sns
        )

        self.assertEqual({"published": 0}, result)
        self.sns.publish.assert_not_called()

    def test_isolates_invalid_batch_specific_contracts_before_valid_event(self):
        task_error = {"type": "TASK_RUN_FAILED", "message": "failed"}
        task_details = {"taskType": "CONTENT_SYNC", "triggerType": "SCHEDULED"}
        invalid_events = [
            (
                "partial_failed_content_sync",
                batch_event("PARTIAL_FAILED", error=task_error),
            ),
            ("stale_content_sync", batch_event("STALE", error=task_error)),
            (
                "partial_failed_other_batch",
                batch_event("PARTIAL_FAILED", batch="other-batch", error=task_error),
            ),
            (
                "stale_other_batch",
                batch_event("STALE", batch="other-batch", error=task_error),
            ),
            (
                "legacy_partial_failure_task_run",
                batch_event(
                    "PARTIAL_FAILURE", batch="task-run", details=task_details
                ),
            ),
            (
                "missing_task_type",
                batch_event(
                    "FAILED",
                    batch="task-run",
                    details={"triggerType": "SCHEDULED"},
                    error=task_error,
                ),
            ),
            (
                "blank_task_type",
                batch_event(
                    "FAILED",
                    batch="task-run",
                    details={"taskType": "  ", "triggerType": "SCHEDULED"},
                    error=task_error,
                ),
            ),
            (
                "non_string_task_type",
                batch_event(
                    "FAILED",
                    batch="task-run",
                    details={"taskType": 1, "triggerType": "SCHEDULED"},
                    error=task_error,
                ),
            ),
            (
                "missing_trigger_type",
                batch_event(
                    "FAILED",
                    batch="task-run",
                    details={"taskType": "CONTENT_SYNC"},
                    error=task_error,
                ),
            ),
            (
                "blank_trigger_type",
                batch_event(
                    "FAILED",
                    batch="task-run",
                    details={"taskType": "CONTENT_SYNC", "triggerType": ""},
                    error=task_error,
                ),
            ),
            (
                "non_string_trigger_type",
                batch_event(
                    "FAILED",
                    batch="task-run",
                    details={"taskType": "CONTENT_SYNC", "triggerType": False},
                    error=task_error,
                ),
            ),
        ]
        valid = batch_event(counts=content_sync_counts(youtubeFailedCount=0))

        for name, invalid in invalid_events:
            with self.subTest(name=name):
                self.sns.reset_mock()
                self.assertIsNone(lambda_function._parse(line(invalid)))

                result = lambda_function.lambda_handler(
                    cloudwatch_event(line(invalid), line(valid)), None, self.sns
                )

                self.assertEqual({"published": 1}, result)
                self.sns.publish.assert_called_once()

    def test_still_accepts_existing_partial_failure_status(self):
        event = batch_event(
            "PARTIAL_FAILURE",
            counts=content_sync_counts(),
        )

        result = lambda_function.lambda_handler(
            cloudwatch_event(line(event)), None, self.sns
        )

        self.assertEqual({"published": 1}, result)
        notification = json.loads(self.sns.publish.call_args.kwargs["Message"])
        self.assertEqual(
            "⚠️ 콘텐츠 동기화 부분 실패", notification["content"]["title"]
        )

    def test_mixed_entries_publish_all_valid_terminal_events(self):
        success = batch_event()
        failed = batch_event(
            "FAILED",
            runId="2284fbed-2d99-422f-a18e-e875055fcb38",
            error={"type": "RuntimeError", "message": "boom"},
        )

        result = lambda_function.lambda_handler(
            cloudwatch_event(
                "not a batch event",
                "BATCH_EVENT {broken",
                line(success),
                line(failed),
            ),
            None,
            self.sns,
        )

        self.assertEqual({"published": 2}, result)
        self.assertEqual(2, self.sns.publish.call_count)
        titles = [
            json.loads(call.kwargs["Message"])["content"]["title"]
            for call in self.sns.publish.call_args_list
        ]
        self.assertEqual(
            ["✅ 콘텐츠 동기화 완료", "🚨 콘텐츠 동기화 실패"], titles
        )

    def test_ignores_control_message(self):
        result = lambda_function.lambda_handler(
            cloudwatch_event(message_type="CONTROL_MESSAGE"), None, self.sns
        )

        self.assertEqual({"published": 0}, result)
        self.sns.publish.assert_not_called()

    def test_suppresses_started(self):
        result = lambda_function.lambda_handler(
            cloudwatch_event(line(batch_event("STARTED"))),
            None,
            self.sns,
        )

        self.assertEqual({"published": 0}, result)
        self.sns.publish.assert_not_called()

    def test_publishes_content_sync_no_targets(self):
        result = lambda_function.lambda_handler(
            cloudwatch_event(line(batch_event(
                "SKIPPED",
                reason="NO_TARGETS",
                counts=content_sync_counts(
                    instagramNewCandidateCount=0,
                    instagramSelectorsContentCount=0,
                    instagramChangedContentCount=0,
                    instagramSavedVersionCount=0,
                    youtubeNewCandidateCount=0,
                    youtubeSelectorsContentCount=0,
                    youtubeChangedContentCount=0,
                    youtubeSavedVersionCount=0,
                    youtubeFailedCount=0,
                ),
            ))),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)
        message = json.loads(self.sns.publish.call_args.kwargs["Message"])
        self.assertEqual("ℹ️ 콘텐츠 동기화 대상 없음", message["content"]["title"])

    def test_accepts_exact_marker_in_rendered_logback_line(self):
        result = lambda_function.lambda_handler(
            cloudwatch_event(
                "2026-08-22T13:40:00.000+09:00  INFO 1 --- [main] "
                "c.f.h.b.logging.BatchEventLogger : "
                + line(batch_event())
            ),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)

    def test_rejects_non_exact_marker(self):
        lambda_function.lambda_handler(
            cloudwatch_event(
                "INFO BATCH_EVENTX " + json.dumps(batch_event()),
                "NOTBATCH_EVENT " + json.dumps(batch_event()),
                "fooBATCH_EVENT " + json.dumps(batch_event()),
            ),
            None,
            self.sns,
        )

        self.sns.publish.assert_not_called()

    def test_rejects_invalid_contract_fields(self):
        missing_required_fields = []
        for field in (
            "schemaVersion",
            "event",
            "batch",
            "runId",
            "status",
            "timestamp",
            "durationMs",
        ):
            invalid = batch_event()
            del invalid[field]
            missing_required_fields.append(invalid)
        invalid_events = [
            *missing_required_fields,
            batch_event(schemaVersion=2),
            batch_event(event="OTHER"),
            batch_event(batch="Content Sync"),
            batch_event(runId="not-a-uuid"),
            batch_event(status="DONE"),
            batch_event(timestamp="2026-08-22T04:40:00"),
            batch_event(durationMs=-1),
            batch_event(durationMs="125"),
            batch_event("STARTED", durationMs=1),
            batch_event("SKIPPED", reason=""),
            batch_event("FAILED"),
        ]

        result = lambda_function.lambda_handler(
            cloudwatch_event(*(line(event) for event in invalid_events)),
            None,
            self.sns,
        )

        self.assertEqual({"published": 0}, result)
        self.sns.publish.assert_not_called()

    def test_isolates_malformed_optional_fields_and_publishes_later_valid_event(self):
        invalid_events = [
            batch_event(counts=[]),
            batch_event(counts={"failed": -1}),
            batch_event(counts={"failed": True}),
            batch_event(details={"nested": {"secret": "value"}}),
            batch_event(reason=123),
            batch_event(error={"type": "RuntimeError"}),
        ]

        result = lambda_function.lambda_handler(
            cloudwatch_event(
                *(line(event) for event in invalid_events),
                line(batch_event(counts=content_sync_counts())),
            ),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_accepts_utc_z_offset_timestamp(self):
        result = lambda_function.lambda_handler(
            cloudwatch_event(line(batch_event(timestamp="2026-08-22T04:40:00Z"))),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)

    def test_sns_failure_propagates(self):
        self.sns.publish.side_effect = ValueError("SNS unavailable")

        with self.assertRaisesRegex(ValueError, "SNS unavailable"):
            lambda_function.lambda_handler(cloudwatch_event(line(batch_event())), None, self.sns)

    def test_requires_topic_arn_only_when_publishing(self):
        del os.environ["SNS_TOPIC_ARN"]

        self.assertEqual(
            {"published": 0},
            lambda_function.lambda_handler(
                cloudwatch_event(line(batch_event("STARTED"))), None, self.sns
            ),
        )
        with self.assertRaisesRegex(RuntimeError, "SNS_TOPIC_ARN"):
            lambda_function.lambda_handler(cloudwatch_event(line(batch_event())), None, self.sns)


if __name__ == "__main__":
    unittest.main()
