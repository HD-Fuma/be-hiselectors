import os
import sys
import types
import unittest
from unittest.mock import patch

import content_media_extraction
import media_stt


STRUCTURED_RESULT = {
    "schemaVersion": "1.1",
    "language": "ko",
    "audio": {"durationMs": 2000, "durationAfterVadMs": 1800},
    "segments": [
        {
            "segmentId": "stt-001",
            "startMs": 0,
            "endMs": 2000,
            "text": "공통 응답",
            "avgLogProb": -0.1,
            "noSpeechProbability": 0.01,
        }
    ],
}


class SagemakerRoutingTest(unittest.TestCase):

    def setUp(self):
        client = types.SimpleNamespace(stt=lambda path: STRUCTURED_RESULT)
        self.sagemaker = types.ModuleType("sagemaker")
        self.sagemaker.client = client

    def test_application_flattens_shared_response_to_text(self):
        with patch.dict(os.environ, {"STT_BACKEND": "sagemaker"}), \
                patch.dict(sys.modules, {"sagemaker": self.sagemaker}), \
                patch.object(media_stt, "_has_audio", return_value=True):
            self.assertEqual("공통 응답", media_stt.stt("media.mp4"))

    def test_content_preserves_shared_structured_response(self):
        with patch.dict(os.environ, {"STT_BACKEND": "sagemaker"}), \
                patch.dict(sys.modules, {"sagemaker": self.sagemaker}), \
                patch.object(media_stt, "_has_audio", return_value=True):
            result = content_media_extraction.stt_segments("media.mp4")

        self.assertEqual("ko", result["language"])
        self.assertEqual(2000, result["audio"]["durationMs"])
        self.assertEqual("공통 응답", result["segments"][0]["text"])


if __name__ == "__main__":
    unittest.main()
