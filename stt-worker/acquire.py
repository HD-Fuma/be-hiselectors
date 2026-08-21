"""릴스 → 로컬 미디어 파일. 파이썬이 직접 취득(Java가 취득 안 함).
취득 우선순위:
  1) media_url(Graph API): scontent CDN 직다운 — 봇체크 없음·안 막힘·ToS 클린. yt-dlp 안 씀.
  2) 없으면(저작권 릴스 등) yt-dlp 로 페이지에서 mp4 취득(홈IP 전제).
  3) 영상 실패 시 thumbnail_url(있으면) 또는 yt-dlp 썸네일 → 음성 없이 OCR만.
차단 우회 쿠키는 env 로만: IG_COOKIES_FROM_BROWSER=chrome / IG_COOKIES=<cookies.txt>."""
from __future__ import annotations

import logging
import os
import tempfile
import urllib.request
from urllib.parse import urlparse

from yt_dlp import YoutubeDL
from yt_dlp.utils import DownloadError

log = logging.getLogger(__name__)


def _cookie_opts() -> dict:
    browser = os.environ.get("IG_COOKIES_FROM_BROWSER")
    if browser:
        return {"cookiesfrombrowser": (browser,)}
    cookiefile = os.environ.get("IG_COOKIES")
    if cookiefile:
        return {"cookiefile": cookiefile}
    return {}


def _base_opts(out_dir: str) -> dict:
    return {
        "outtmpl": os.path.join(out_dir, "%(id)s.%(ext)s"),
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        **_cookie_opts(),
    }


def _download_media(url: str, out_dir: str) -> str:
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


def _download_video(url: str, out_dir: str) -> str:
    opts = {**_base_opts(out_dir), "format": "mp4/bestvideo+bestaudio/best"}
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)


def _download_thumb(url: str, out_dir: str) -> str:
    """yt-dlp info 에서 썸네일만. 완전 로그인벽이면 이것도 실패(그땐 쿠키 필요)."""
    with YoutubeDL(_base_opts(out_dir)) as ydl:
        info = ydl.extract_info(url, download=False)
    thumb = info.get("thumbnail") or next(
        (t["url"] for t in reversed(info.get("thumbnails") or []) if t.get("url")), None
    )
    if not thumb:
        raise DownloadError("영상·썸네일 모두 취득 실패")
    return _download_media(thumb, out_dir)


def fetch(url: str | None = None, media_url: str | None = None,
          thumbnail_url: str | None = None, out_dir: str | None = None) -> str:
    """취득 우선순위대로 로컬 파일 경로 반환(mp4 또는 썸네일 jpg). 호출부가 쓰고 삭제한다."""
    out_dir = out_dir or tempfile.mkdtemp(prefix="reel_")

    # 1) media_url 우선 — CDN 직다운(안 막힘). yt-dlp 불필요.
    if media_url:
        try:
            return _download_media(media_url, out_dir)
        except OSError as e:
            log.warning("media_url 다운로드 실패(%s), 다음 경로 시도", e)

    # 2) yt-dlp 로 영상 취득.
    if url:
        try:
            return _download_video(url, out_dir)
        except DownloadError:
            log.info("영상 취득 실패 → 썸네일 폴백")

    # 3) 썸네일 폴백(음성 없이 OCR만).
    if thumbnail_url:
        try:
            return _download_media(thumbnail_url, out_dir)
        except OSError as e:
            log.warning("thumbnail_url 다운로드 실패(%s)", e)
    if url:
        return _download_thumb(url, out_dir)

    raise DownloadError("취득 소스 없음(url·media_url·thumbnail_url 전부 없음)")
