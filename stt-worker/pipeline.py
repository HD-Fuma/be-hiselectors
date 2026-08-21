"""릴스 URL 하나 → 취득 → STT/OCR → 정성 분석. 전 과정 무저장(끝나면 파일 삭제).
CLI:  python pipeline.py <릴스URL>
서버: serve.py 의 POST /reel 이 run() 을 부른다."""
from __future__ import annotations

import os
import shutil

import acquire
import analyze
import media_stt


def run(url: str | None = None, media_url: str | None = None,
        thumbnail_url: str | None = None, include_hate: bool = True) -> dict:
    """{source, stt, ocr, analysis}. media_url 있으면 CDN 직다운(yt-dlp 안 씀), 없으면 url 로 yt-dlp.
    다운로드 파일은 분석 후 삭제(비동의 원문 미보관)."""
    path = acquire.fetch(url=url, media_url=media_url, thumbnail_url=thumbnail_url)
    work_dir = os.path.dirname(path)
    try:
        t = media_stt.transcribe(path)
        text = f"{t['stt']} {t['ocr']}".strip()
        return {
            "source": "thumbnail" if not t["stt"] else "video",
            "stt": t["stt"],
            "ocr": t["ocr"],
            "analysis": analyze.analyze(text, include_hate=include_hate),
        }
    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    import json
    import sys

    if len(sys.argv) != 2:
        sys.exit("usage: python pipeline.py <릴스URL>")
    print(json.dumps(run(sys.argv[1]), ensure_ascii=False, indent=2))
