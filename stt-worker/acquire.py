"""릴스 URL → 로컬 미디어 파일. 파이썬이 직접 취득(Java가 URL 안 넘김).
- 기본: yt-dlp 로 mp4 다운로드(홈IP 전제).
- 실패(차단/저작권/로그인벽): 썸네일 이미지로 폴백 → 음성 없이 OCR만.
차단 우회용 쿠키는 env 로만: IG_COOKIES_FROM_BROWSER=chrome  또는  IG_COOKIES=<cookies.txt>.
쿠키 스크래핑은 ToS 리스크라 기본 꺼둔다."""
from __future__ import annotations

import os
import tempfile
import urllib.request

from yt_dlp import YoutubeDL
from yt_dlp.utils import DownloadError


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


def _download_video(url: str, out_dir: str) -> str:
    opts = {**_base_opts(out_dir), "format": "mp4/bestvideo+bestaudio/best"}
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)


def _download_thumb(url: str, out_dir: str) -> str:
    """영상을 못 받을 때 커버 이미지만. extract_info 는 페이지 접근이 되어야 하므로
    완전 로그인벽이면 이것도 실패한다(그땐 쿠키 필요)."""
    with YoutubeDL(_base_opts(out_dir)) as ydl:
        info = ydl.extract_info(url, download=False)
    thumb = info.get("thumbnail") or next(
        (t["url"] for t in reversed(info.get("thumbnails") or []) if t.get("url")), None
    )
    if not thumb:
        raise DownloadError("영상·썸네일 모두 취득 실패")
    path = os.path.join(out_dir, f"{info.get('id', 'cover')}.jpg")
    urllib.request.urlretrieve(thumb, path)
    return path


def fetch(url: str, out_dir: str | None = None) -> str:
    """릴스 URL → 로컬 파일 경로(mp4 또는 썸네일 jpg). 호출부가 쓰고 삭제한다."""
    out_dir = out_dir or tempfile.mkdtemp(prefix="reel_")
    try:
        return _download_video(url, out_dir)
    except DownloadError:
        return _download_thumb(url, out_dir)
