"""지원서와 콘텐츠 검수가 공유하는 SageMaker whisper large-v3 핸들러."""
import json
import math
import os
import tempfile

from faster_whisper import WhisperModel


def model_fn(model_dir):
    # 가중치를 model.tar.gz 에 구워 로컬 로드(HF 재다운로드 없음 → 콜드스타트 단축).
    weights = os.path.join(model_dir, "models", "large-v3")
    return WhisperModel(weights, device="cuda", compute_type="float16")


def input_fn(request_body, content_type=None):
    # 바디(오디오/영상 바이트)를 임시파일로. faster-whisper 가 av 로 디코드.
    suffix = ".mp4" if content_type and "mp4" in content_type else ".media"
    fd, path = tempfile.mkstemp(suffix=suffix)
    with os.fdopen(fd, "wb") as f:
        f.write(request_body)
    return path


def predict_fn(path, model):
    try:
        segments, info = model.transcribe(path, language="ko", vad_filter=True)
        result = []
        for segment in segments:
            text = segment.text.strip()
            if not text:
                continue
            start_ms = max(0, round(float(segment.start) * 1000))
            end_ms = max(start_ms + 1, round(float(segment.end) * 1000))
            result.append({
                "segmentId": f"stt-{len(result) + 1:03d}",
                "startMs": start_ms,
                "endMs": end_ms,
                "text": text,
                "avgLogProb": _finite(segment.avg_logprob),
                "noSpeechProbability": _finite(segment.no_speech_prob),
            })
        return {
            "schemaVersion": "1.1",
            "language": info.language or "",
            "audio": {
                "durationMs": _milliseconds(info.duration),
                "durationAfterVadMs": _milliseconds(info.duration_after_vad),
            },
            "segments": result,
        }
    finally:
        os.remove(path)


def _finite(value):
    number = float(value)
    return round(number, 6) if math.isfinite(number) else None


def _milliseconds(seconds):
    number = float(seconds)
    return max(0, round(number * 1000)) if math.isfinite(number) else None


def output_fn(prediction, accept=None):
    return json.dumps(prediction, ensure_ascii=False), "application/json"
