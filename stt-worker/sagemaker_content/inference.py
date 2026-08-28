"""콘텐츠 검수 전용 faster-whisper SageMaker 추론 핸들러."""
import json
import os
import tempfile

from faster_whisper import WhisperModel


def model_fn(model_dir):
    weights = os.path.join(model_dir, "models", "large-v3")
    return WhisperModel(weights, device="cuda", compute_type="float16")


def input_fn(request_body, content_type=None):
    suffix = ".mp4" if content_type and "mp4" in content_type else ".media"
    fd, path = tempfile.mkstemp(suffix=suffix)
    with os.fdopen(fd, "wb") as file:
        file.write(request_body)
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
            })
        return {"language": info.language or "", "segments": result}
    finally:
        os.remove(path)


def output_fn(prediction, accept=None):
    return json.dumps(prediction, ensure_ascii=False), "application/json"
