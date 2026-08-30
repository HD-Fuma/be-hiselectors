import unittest

from stt_contract import content_stt, transcript_text


class SttContractTest(unittest.TestCase):

    def test_flattens_structured_segments_for_application_report(self):
        result = {
            "schemaVersion": "1.1",
            "segments": [
                {"segmentId": "stt-001", "text": "첫 발화"},
                {"segmentId": "stt-002", "text": " 두 번째 발화 "},
            ],
        }

        self.assertEqual("첫 발화 두 번째 발화", transcript_text(result))

    def test_keeps_legacy_string_during_endpoint_migration(self):
        self.assertEqual("기존 전사", transcript_text({"stt": " 기존 전사 "}))

    def test_normalizes_content_audio_metadata(self):
        result = content_stt({
            "language": "ko",
            "audio": {"durationMs": 1000, "durationAfterVadMs": 700},
            "segments": [{"segmentId": "stt-001", "text": "발화"}],
        })

        self.assertEqual(700, result["audio"]["durationAfterVadMs"])
        self.assertEqual(1, len(result["segments"]))


if __name__ == "__main__":
    unittest.main()
