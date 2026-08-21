"""Instagram 콘텐츠 취득 — Graph API 미디어 URL(공식 API, ToS 준수)만 사용.
yt-dlp 등 스크래핑은 인스타그램 이용약관 위반이라 절대 쓰지 않는다.
- media_url(scontent CDN): 영상/이미지 직다운(봇체크 없음, IP 무관, 합법).
- 없으면(저작권 릴스 등) thumbnail_url 이미지로 폴백 → 음성 없이 OCR만.
둘 다 없으면 취득 불가(AcquireError)."""
from __future__ import annotations

import os
import tempfile
import urllib.request
from urllib.parse import urlparse


class AcquireError(Exception):
    pass


def _download(url: str, out_dir: str) -> str:
    """CDN 직다운. 확장자는 Content-Type(→URL경로)으로 결정해 영상/이미지 모두 처리."""
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        ctype = resp.headers.get("Content-Type", "")
        data = resp.read()
    if "video" in ctype:
        ext = ".mp4"
    elif "image" in ctype:
        ext = ".jpg"
    else:
        ext = os.path.splitext(urlparse(url).path)[1] or ".mp4"
    path = os.path.join(out_dir, "media" + ext)
    with open(path, "wb") as f:
        f.write(data)
    return path


def fetch(media_url: str | None = None, thumbnail_url: str | None = None,
          out_dir: str | None = None) -> str:
    """Graph API URL로 로컬 파일 경로 반환(mp4 또는 썸네일 jpg). 호출부가 쓰고 삭제한다."""
    out_dir = out_dir or tempfile.mkdtemp(prefix="ig_")

    # 1) media_url(공식) — CDN 직다운. 영상이면 STT+OCR, 이미지면 OCR.
    if media_url:
        return _download(media_url, out_dir)

    # 2) 저작권 릴스 등 media_url 없으면 썸네일 이미지로 폴백(음성 없이 OCR만).
    if thumbnail_url:
        return _download(thumbnail_url, out_dir)

    raise AcquireError("취득 소스 없음 — Graph API media_url/thumbnail_url 필요")
