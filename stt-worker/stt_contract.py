"""지원서와 콘텐츠 검수가 공유하는 구조화 STT 응답 계약."""
from __future__ import annotations

SCHEMA_VERSION = "1.2"


def transcript_text(result: dict | None) -> str:
    """구조화 세그먼트를 지원서용 단일 문자열로 평탄화한다.

    배포 전환 중 기존 ``{"stt": "..."}`` 응답도 읽을 수 있게 유지한다.
    """
    if not result:
        return ""
    legacy = result.get("stt")
    if isinstance(legacy, str):
        return legacy.strip()
    segments = result.get("segments")
    if not isinstance(segments, list):
        return ""
    return " ".join(
        text.strip()
        for segment in segments
        if isinstance(segment, dict)
        and isinstance((text := segment.get("text")), str)
        and text.strip()
    )


def content_stt(result: dict | None) -> dict:
    """SageMaker 응답에서 콘텐츠 검수에 저장할 STT 부분만 정규화한다."""
    value = result or {}
    audio = value.get("audio")
    segments = value.get("segments")
    return {
        "language": value.get("language", ""),
        "audio": audio if isinstance(audio, dict) else {
            "durationMs": None,
            "durationAfterVadMs": None,
        },
        "segments": segments if isinstance(segments, list) else [],
    }
