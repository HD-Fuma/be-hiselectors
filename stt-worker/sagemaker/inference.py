"""SageMaker 추론 핸들러 — whisper large-v3 STT.
Async Inference 엔드포인트에 올린다. 요청 바디 = 오디오/영상 바이트, 응답 = {stt, language}.
CPU 로컬(media_stt.py)과 분리 — 무거운 whisper 만 GPU(scale-to-zero)로 오프로드한다."""
import json
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
        text = " ".join(seg.text.strip() for seg in segments).strip()
        return {"stt": text, "language": info.language}
    finally:
        os.remove(path)


def output_fn(prediction, accept=None):
    return json.dumps(prediction, ensure_ascii=False), "application/json"
