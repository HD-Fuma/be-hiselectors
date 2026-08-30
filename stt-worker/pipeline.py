"""Graph API 미디어 URL → 취득 → STT/OCR → 정성 분석. 전 과정 무저장(끝나면 파일 삭제).
서버: serve.py 의 POST /reel 이 run() 을 부른다. (yt-dlp 미사용 — ToS 준수, media_url만)"""
from __future__ import annotations

import os
import shutil

import acquire
import analyze
import content_media_extraction
import media_stt


def run(media_url: str | None = None, thumbnail_url: str | None = None) -> dict:
    """{source, stt, ocr, analysis}. media_url(mp4/이미지) 있으면 CDN 직다운, 없으면 thumbnail_url 폴백.
    다운로드 파일은 분석 후 삭제(비동의 원문 미보관)."""
    path = acquire.fetch(media_url=media_url, thumbnail_url=thumbnail_url)
    work_dir = os.path.dirname(path)
    try:
        t = media_stt.transcribe(path)
        text = f"{t['stt']} {t['ocr']}".strip()
        return {
            "source": "thumbnail" if not t["stt"] else "video",
            "stt": t["stt"],
            "ocr": t["ocr"],
            "analysis": analyze.analyze(text),
        }
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


def run_content(media_url: str | None = None, thumbnail_url: str | None = None) -> dict:
    """콘텐츠 검수 전용 구조화 STT/OCR. 지원서 분석 결과는 생성하지 않는다."""
    path = acquire.fetch(media_url=media_url, thumbnail_url=thumbnail_url)
    work_dir = os.path.dirname(path)
    try:
        return content_media_extraction.extract(path)
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    import json
    import sys

    if len(sys.argv) != 2:
        sys.exit("usage: python pipeline.py <media_url>")
    print(json.dumps(run(media_url=sys.argv[1]), ensure_ascii=False, indent=2))
