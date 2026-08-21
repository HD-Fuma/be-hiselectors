"""Instagram 콘텐츠 취득 — Graph API 미디어 URL(공식 API, ToS 준수)만 사용.
yt-dlp 등 스크래핑은 인스타그램 이용약관 위반이라 절대 쓰지 않는다.
- media_url(scontent CDN): 영상/이미지 직다운(봇체크 없음, IP 무관, 합법).
- 없으면(저작권 릴스 등) thumbnail_url 이미지로 폴백 → 음성 없이 OCR만.
둘 다 없으면 취득 불가(AcquireError)."""
from __future__ import annotations

import os
import re
import tempfile
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse


class AcquireError(Exception):
    pass


class CdnExpiredError(AcquireError):
    """media_url/thumbnail_url(scontent CDN)이 만료됨. 호출부가 Graph API로 fresh URL 재요청해야 함."""


def _is_expired(url: str) -> bool:
    """scontent URL의 oe=HEX 는 만료시각(unix). 지났으면 만료. 여유 60초."""
    m = re.search(r"[?&]oe=([0-9A-Fa-f]+)", url)
    if not m:
        return False
    try:
        return int(m.group(1), 16) < (time.time() + 60)
    except ValueError:
        return False


def _download(url: str, out_dir: str) -> str:
    """CDN 직다운. 확장자는 Content-Type(→URL경로)으로 결정해 영상/이미지 모두 처리.
    만료 URL(oe 지남 또는 403/410)은 CdnExpiredError 로 구분해 던진다."""
    if _is_expired(url):
        raise CdnExpiredError("media_url 만료(oe 시각 지남)")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        resp = urllib.request.urlopen(req, timeout=60)
    except urllib.error.HTTPError as e:
        if e.code in (403, 410):
            raise CdnExpiredError(f"CDN {e.code} — media_url 만료/무효") from e
        raise
    with resp:
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
