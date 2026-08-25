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
RUN_ID = "2284fbed-2d99-422f-a18e-e875055fcb38"


def batch_event(status="FAILED", **overrides):
    event = {
        "schemaVersion": 1,
        "event": "BATCH_RUN",
        "batch": "task-run",
        "runId": RUN_ID,
        "status": status,
        "timestamp": "2026-08-22T13:40:00+09:00",
        "durationMs": 84_000,
        "details": {"taskType": "CONTENT_SYNC", "triggerType": "SCHEDULED"},
        "error": {"type": "TASK_RUN_FAILED", "message": "콘텐츠 저장에 실패했습니다."},
    }
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


def notification(sns):
    return json.loads(sns.publish.call_args.kwargs["Message"])["content"]


class LambdaHandlerTest(unittest.TestCase):
    def setUp(self):
        self.sns = Mock()
        self.environment = patch.dict(
            os.environ, {"SNS_TOPIC_ARN": TOPIC_ARN}, clear=True
        )
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def publish(self, event):
        return lambda_function.lambda_handler(
            cloudwatch_event(line(event)), None, self.sns
        )

    def test_publishes_exact_failed_task_run_notification(self):
        event = batch_event(
            counts={"total": 12, "processed": 12, "succeeded": 9, "failed": 3}
        )

        result = self.publish(event)

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once_with(
            TopicArn=TOPIC_ARN,
            Message=json.dumps(
                {
                    "version": "1.0",
                    "source": "custom",
                    "content": {
                        "textType": "client-markdown",
                        "title": "🚨 콘텐츠 수집 실패",
                        "description": (
                            "실행 방식: 자동 실행\n"
                            "처리 결과: 전체 12건 / 성공 9건 / 실패 3건\n"
                            "실패 원인: 콘텐츠 저장에 실패했습니다.\n"
                            "실행 시간: 1분 24초\n"
                            "실행 ID: 2284fbed-2d99-422f-a18e-e875055fcb38"
                        ),
                    },
                },
                ensure_ascii=False,
                separators=(",", ":"),
            ),
        )

    def test_publishes_status_specific_titles(self):
        cases = {
            "FAILED": "🚨 콘텐츠 수집 실패",
            "PARTIAL_FAILED": "⚠️ 콘텐츠 수집 일부 실패",
            "STALE": "⏱️ 콘텐츠 수집 비정상 종료",
        }
        for status, expected in cases.items():
            with self.subTest(status=status):
                self.sns.reset_mock()
                self.publish(batch_event(status))
                self.assertEqual(expected, notification(self.sns)["title"])

    def test_maps_all_task_types_to_korean_labels(self):
        labels = {
            "CREATOR_SYNC": "크리에이터 수집",
            "CONTENT_SYNC": "콘텐츠 수집",
            "APPLICATION_REPORT_GENERATION": "지원자 리포트 생성",
            "CONTENT_REPORT_GENERATION": "콘텐츠 검수 리포트 생성",
            "SETTLEMENT_CALCULATION": "정산 계산",
            "KAKAO_MESSAGE_SEND": "카카오 메시지 발송",
            "PROPOSAL_EMAIL_SEND": "제안 이메일 발송",
        }
        for task_type, label in labels.items():
            with self.subTest(task_type=task_type):
                self.sns.reset_mock()
                self.publish(
                    batch_event(
                        details={"taskType": task_type, "triggerType": "ADMIN_TRIGGERED"}
                    )
                )
                content = notification(self.sns)
                self.assertEqual("🚨 " + label + " 실패", content["title"])
                self.assertIn("실행 방식: 관리자 실행", content["description"])

    def test_uses_fixed_labels_for_unknown_task_and_trigger(self):
        hostile_task = "UNKNOWN<@U123>\n" + "x" * 480
        hostile_trigger = "UNKNOWN<https://example.com>\t" + "y" * 465
        event = batch_event(
            details={"taskType": hostile_task, "triggerType": hostile_trigger}
        )

        result = lambda_function.lambda_handler(
            cloudwatch_event(line(event), line(batch_event())), None, self.sns
        )

        self.assertEqual({"published": 2}, result)
        first = json.loads(self.sns.publish.call_args_list[0].kwargs["Message"])["content"]
        second = json.loads(self.sns.publish.call_args_list[1].kwargs["Message"])["content"]
        self.assertEqual("🚨 알 수 없는 작업 실패", first["title"])
        self.assertIn("실행 방식: 알 수 없는 실행", first["description"])
        self.assertNotIn("UNKNOWN", json.dumps(first, ensure_ascii=False))
        self.assertEqual("🚨 콘텐츠 수집 실패", second["title"])

    def test_escapes_error_markdown_mentions_links_and_html(self):
        raw_error = "<@U123> & <https://example.com|링크> > 실패"
        self.publish(
            batch_event(error={"type": "RuntimeError", "message": raw_error})
        )

        description = notification(self.sns)["description"]
        self.assertIn(
            "실패 원인: &lt;@U123&gt; &amp; &lt;https://example.com|링크&gt; &gt; 실패",
            description,
        )
        self.assertNotIn(raw_error, description)

    def test_formats_total_counts_when_total_is_present(self):
        self.publish(
            batch_event(
                counts={"total": 10, "processed": 8, "succeeded": 6, "failed": 2, "skipped": 1}
            )
        )
        self.assertIn(
            "처리 결과: 전체 10건 / 성공 6건 / 실패 2건",
            notification(self.sns)["description"],
        )

    def test_formats_processed_counts_without_total(self):
        self.publish(
            batch_event(
                counts={"processed": 8, "succeeded": 6, "failed": 2, "skipped": 1}
            )
        )
        self.assertIn(
            "처리 결과: 처리 8건 / 성공 6건 / 실패 2건",
            notification(self.sns)["description"],
        )

    def test_omits_counts_when_every_count_is_zero(self):
        self.publish(
            batch_event(
                counts={"processed": 0, "succeeded": 0, "failed": 0, "skipped": 0}
            )
        )
        self.assertNotIn("처리 결과:", notification(self.sns)["description"])

    def test_omits_counts_when_counts_are_absent(self):
        self.publish(batch_event())
        self.assertNotIn("처리 결과:", notification(self.sns)["description"])

    def test_formats_duration_boundaries(self):
        cases = {
            999: "0초",
            59_999: "59초",
            60_000: "1분 0초",
            3_599_999: "59분 59초",
            3_600_000: "1시간 0분 0초",
            7_261_999: "2시간 1분 1초",
        }
        for duration_ms, expected in cases.items():
            with self.subTest(duration_ms=duration_ms):
                self.sns.reset_mock()
                self.publish(batch_event(durationMs=duration_ms))
                self.assertIn(
                    "실행 시간: " + expected, notification(self.sns)["description"]
                )

    def test_suppresses_succeeded_task_run(self):
        event = batch_event("SUCCEEDED")
        event.pop("error")
        result = self.publish(event)
        self.assertEqual({"published": 0}, result)
        self.sns.publish.assert_not_called()

    def test_suppresses_all_legacy_content_sync_statuses(self):
        for status in ("STARTED", "SUCCEEDED", "PARTIAL_FAILURE", "FAILED", "SKIPPED"):
            with self.subTest(status=status):
                self.sns.reset_mock()
                event = batch_event(status, batch="content-sync")
                event.pop("details")
                if status == "STARTED":
                    event.pop("durationMs")
                    event.pop("error")
                elif status in {"SUCCEEDED", "PARTIAL_FAILURE"}:
                    event.pop("error")
                elif status == "SKIPPED":
                    event.pop("error")
                    event["reason"] = "NO_TARGETS"
                result = self.publish(event)
                self.assertEqual({"published": 0}, result)
                self.sns.publish.assert_not_called()

    def test_paired_legacy_and_task_run_failures_publish_one_task_run_message(self):
        legacy = batch_event("FAILED", batch="content-sync")
        legacy.pop("details")
        task_run = batch_event("FAILED")
        result = lambda_function.lambda_handler(
            cloudwatch_event(line(legacy), line(task_run)), None, self.sns
        )
        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()
        self.assertEqual("🚨 콘텐츠 수집 실패", notification(self.sns)["title"])

    def test_preserves_docker_envelope_unicode_and_control_message(self):
        docker_message = json.dumps(
            {"log": line(batch_event()) + "\n", "stream": "stdout"}
        )
        result = lambda_function.lambda_handler(
            cloudwatch_event(docker_message), None, self.sns
        )
        self.assertEqual({"published": 1}, result)
        self.assertIn("콘텐츠", self.sns.publish.call_args.kwargs["Message"])

        self.sns.reset_mock()
        result = lambda_function.lambda_handler(
            cloudwatch_event(message_type="CONTROL_MESSAGE"), None, self.sns
        )
        self.assertEqual({"published": 0}, result)
        self.sns.publish.assert_not_called()

    def test_isolates_invalid_contract_before_valid_event(self):
        invalid = batch_event(runId="not-a-uuid")
        valid = batch_event()
        result = lambda_function.lambda_handler(
            cloudwatch_event(line(invalid), line(valid)), None, self.sns
        )
        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_accepts_exact_marker_in_rendered_log_and_rejects_near_markers(self):
        rendered = (
            "2026-08-22T13:40:00.000+09:00 INFO "
            "c.f.h.b.logging.BatchEventLogger : "
            + line(batch_event())
        )
        result = lambda_function.lambda_handler(
            cloudwatch_event(
                "INFO BATCH_EVENTX " + json.dumps(batch_event()),
                "NOTBATCH_EVENT " + json.dumps(batch_event()),
                "fooBATCH_EVENT " + json.dumps(batch_event()),
                rendered,
            ),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_rejects_invalid_required_and_core_fields_before_valid_event(self):
        invalid_events = []
        for field in (
            "schemaVersion",
            "event",
            "batch",
            "runId",
            "status",
            "timestamp",
            "durationMs",
        ):
            event = batch_event()
            del event[field]
            invalid_events.append(event)
        invalid_events.extend(
            [
                batch_event(schemaVersion=True),
                batch_event(schemaVersion=2),
                batch_event(event="OTHER"),
                batch_event(batch="Task Run"),
                batch_event(runId="not-a-uuid"),
                batch_event(status="DONE"),
                batch_event(timestamp="2026-08-22T04:40:00"),
                batch_event(durationMs=-1),
                batch_event(durationMs="84000"),
            ]
        )
        for name, invalid in (
            ("invalid_batch_name", batch_event(batch="Task Run")),
            ("invalid_status", batch_event(status="DONE")),
        ):
            with self.subTest(name=name):
                self.assertIsNone(lambda_function._parse(line(invalid)))

        result = lambda_function.lambda_handler(
            cloudwatch_event(
                *(line(event) for event in invalid_events),
                line(batch_event()),
            ),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_rejects_incompatible_batch_status_pairs_before_valid_event(self):
        invalid_events = [
            ("task_run_partial_failure", batch_event("PARTIAL_FAILURE")),
            (
                "content_sync_partial_failed",
                batch_event("PARTIAL_FAILED", batch="content-sync"),
            ),
            ("content_sync_stale", batch_event("STALE", batch="content-sync")),
            (
                "other_batch_partial_failed",
                batch_event("PARTIAL_FAILED", batch="other-batch"),
            ),
            ("other_batch_stale", batch_event("STALE", batch="other-batch")),
        ]

        for name, invalid in invalid_events:
            with self.subTest(name=name):
                self.assertIsNone(lambda_function._parse(line(invalid)))

        result = lambda_function.lambda_handler(
            cloudwatch_event(
                *(line(event) for _, event in invalid_events),
                line(batch_event()),
            ),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_isolates_invalid_task_details_and_status_fields(self):
        task_error = {"type": "TASK_RUN_FAILED", "message": "failed"}
        detail_cases = [
            ("missing_task_type", {"triggerType": "SCHEDULED"}),
            ("blank_task_type", {"taskType": "  ", "triggerType": "SCHEDULED"}),
            ("non_string_task_type", {"taskType": 1, "triggerType": "SCHEDULED"}),
            ("missing_trigger_type", {"taskType": "CONTENT_SYNC"}),
            ("blank_trigger_type", {"taskType": "CONTENT_SYNC", "triggerType": ""}),
            ("non_string_trigger_type", {"taskType": "CONTENT_SYNC", "triggerType": False}),
        ]
        invalid_events = [
            (name, batch_event(details=details, error=task_error))
            for name, details in detail_cases
        ]

        missing_error = batch_event()
        missing_error.pop("error")
        invalid_events.append(("failed_without_error", missing_error))

        started_with_duration = batch_event("STARTED", batch="content-sync")
        started_with_duration.pop("details")
        started_with_duration.pop("error")
        invalid_events.append(("started_with_duration", started_with_duration))

        for name, reason in (("skipped_without_reason", None), ("skipped_with_blank_reason", "")):
            skipped = batch_event("SKIPPED", batch="content-sync")
            skipped.pop("details")
            skipped.pop("error")
            if reason is not None:
                skipped["reason"] = reason
            invalid_events.append((name, skipped))

        for name, invalid in invalid_events:
            with self.subTest(name=name):
                self.sns.reset_mock()
                self.assertIsNone(lambda_function._parse(line(invalid)))
                result = lambda_function.lambda_handler(
                    cloudwatch_event(line(invalid), line(batch_event())), None, self.sns
                )
                self.assertEqual({"published": 1}, result)
                self.sns.publish.assert_called_once()

    def test_isolates_malformed_optional_fields_before_valid_event(self):
        invalid_events = [
            batch_event(counts=[]),
            batch_event(counts={"failed": -1}),
            batch_event(counts={"failed": True}),
            batch_event(details={"nested": {"secret": "value"}}),
            batch_event(reason=123),
            batch_event(error={"type": "RuntimeError"}),
            batch_event(error={"type": "", "message": "failed"}),
        ]

        result = lambda_function.lambda_handler(
            cloudwatch_event(
                *(line(event) for event in invalid_events),
                line(batch_event()),
            ),
            None,
            self.sns,
        )

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_accepts_utc_z_timestamp(self):
        result = self.publish(batch_event(timestamp="2026-08-22T04:40:00Z"))

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_enforces_error_message_500_character_boundary(self):
        accepted = batch_event(
            error={"type": "RuntimeError", "message": "x" * 500}
        )
        rejected = batch_event(
            error={"type": "RuntimeError", "message": "x" * 501}
        )

        result = lambda_function.lambda_handler(
            cloudwatch_event(line(accepted), line(rejected)), None, self.sns
        )

        self.assertEqual({"published": 1}, result)
        self.sns.publish.assert_called_once()

    def test_requires_topic_only_when_publishing_and_propagates_sns_failure(self):
        del os.environ["SNS_TOPIC_ARN"]
        succeeded = batch_event("SUCCEEDED")
        succeeded.pop("error")
        self.assertEqual({"published": 0}, self.publish(succeeded))
        with self.assertRaisesRegex(RuntimeError, "SNS_TOPIC_ARN"):
            self.publish(batch_event())

        os.environ["SNS_TOPIC_ARN"] = TOPIC_ARN
        self.sns.publish.side_effect = ValueError("SNS unavailable")
        with self.assertRaisesRegex(ValueError, "SNS unavailable"):
            self.publish(batch_event())


if __name__ == "__main__":
    unittest.main()
