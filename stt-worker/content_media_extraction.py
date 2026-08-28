"""콘텐츠 검수 전용 STT/OCR 세그먼트 추출.

지원서용 media_stt.transcribe()의 단일 문자열 계약을 변경하지 않는다.
"""
from __future__ import annotations

import os

import media_stt

SCHEMA_VERSION = "1.0"


def _segment_id(prefix: str, index: int) -> str:
    return f"{prefix}-{index:03d}"


def _time_ms(seconds: float) -> int:
    return max(0, round(float(seconds) * 1000))


def stt_segments(path: str) -> dict:
    if not media_stt._has_audio(path):
        return {"language": "", "segments": []}

    if os.environ.get("CONTENT_STT_BACKEND") == "sagemaker":
        from sagemaker_content import client
        result = client.stt(path)
        return {
            "language": result.get("language", ""),
            "segments": result.get("segments", []),
        }

    segments, info = media_stt._whisper().transcribe(
        path, language="ko", vad_filter=True)
    result = []
    for segment in segments:
        text = segment.text.strip()
        if not text:
            continue
        start_ms = _time_ms(segment.start)
        end_ms = max(start_ms + 1, _time_ms(segment.end))
        result.append({
            "segmentId": _segment_id("stt", len(result) + 1),
            "startMs": start_ms,
            "endMs": end_ms,
            "text": text,
        })
    return {"language": info.language or "", "segments": result}


def _frames(path: str, every: float = media_stt.SAMPLE_EVERY):
    import av
    with av.open(path) as container:
        stream = container.streams.video[0]
        next_t = 0.0
        for frame in container.decode(stream):
            timestamp = float(frame.pts * stream.time_base) \
                if frame.pts is not None else next_t
            if timestamp >= next_t:
                yield timestamp, frame.to_ndarray(format="bgr24")
                next_t = timestamp + every


def _normalized_bbox(box, image) -> dict | None:
    if box is None or len(box) == 0:
        return None
    height, width = image.shape[:2]
    if width <= 0 or height <= 0:
        return None
    xs = [float(point[0]) for point in box]
    ys = [float(point[1]) for point in box]
    x1 = min(max(min(xs) / width, 0.0), 1.0)
    y1 = min(max(min(ys) / height, 0.0), 1.0)
    x2 = min(max(max(xs) / width, x1), 1.0)
    y2 = min(max(max(ys) / height, y1), 1.0)
    if x2 <= x1 or y2 <= y1:
        return None
    return {"x": x1, "y": y1, "width": x2 - x1, "height": y2 - y1}


def _ocr_detections(image) -> list[tuple[str, dict]]:
    result = media_stt._ocr()(image)
    texts = None if result is None else result.txts
    boxes = None if result is None else getattr(result, "boxes", None)
    if texts is None or boxes is None or len(texts) == 0 or len(boxes) == 0:
        return []
    scores = result.scores
    if scores is None or len(scores) == 0:
        scores = [1.0] * len(texts)
    detections = []
    for text, score, box in zip(texts, scores, boxes):
        normalized = _normalized_bbox(box, image)
        value = text.strip()
        if value and float(score) >= media_stt.MIN_OCR_SCORE and normalized:
            detections.append((value, normalized))
    return detections


def _same_bbox(left: dict, right: dict, tolerance: float = 0.05) -> bool:
    return all(abs(float(left[key]) - float(right[key])) <= tolerance
               for key in ("x", "y", "width", "height"))


def ocr_video_segments(path: str) -> list[dict]:
    segments = []
    sample_ms = max(1, _time_ms(media_stt.SAMPLE_EVERY))
    for timestamp, image in _frames(path):
        start_ms = _time_ms(timestamp)
        end_ms = start_ms + sample_ms
        for text, bbox in _ocr_detections(image):
            previous = next((segment for segment in reversed(segments)
                             if segment["text"] == text
                             and start_ms <= segment["endMs"] + sample_ms
                             and _same_bbox(segment["bbox"], bbox)), None)
            if previous:
                previous["endMs"] = end_ms
                continue
            segments.append({
                "segmentId": _segment_id("ocr", len(segments) + 1),
                "startMs": start_ms,
                "endMs": end_ms,
                "text": text,
                "coordinateSpace": "NORMALIZED",
                "bbox": bbox,
            })
    return segments


def ocr_image_segments(path: str) -> list[dict]:
    import av
    with av.open(path) as container:
        frame = next(container.decode(video=0), None)
        if frame is None:
            return []
        image = frame.to_ndarray(format="bgr24")
    return [{
        "segmentId": _segment_id("ocr", index),
        "startMs": None,
        "endMs": None,
        "text": text,
        "coordinateSpace": "NORMALIZED",
        "bbox": bbox,
    } for index, (text, bbox) in enumerate(_ocr_detections(image), start=1)]


def extract(path: str) -> dict:
    extension = os.path.splitext(path)[1].lower()
    is_image = extension in media_stt.IMAGE_EXT
    return {
        "schemaVersion": SCHEMA_VERSION,
        "stt": {"language": "", "segments": []} if is_image else stt_segments(path),
        "ocr": {"segments": ocr_image_segments(path) if is_image
                else ocr_video_segments(path)},
        "visual": {"segments": []},
    }
