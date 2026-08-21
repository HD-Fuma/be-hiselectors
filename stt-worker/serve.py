"""테스트용 로컬 서버. transcript 텍스트 → 정성 5필드 JSON.
실행: python serve.py  → http://127.0.0.1:8900 접속.
Java 없이 엔진만 단독으로 확인하는 용도."""
import logging
import traceback
from pathlib import Path

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel

import analyze as engine
import pipeline

app = FastAPI(title="정성 엔진 테스트")
_PAGE = Path(__file__).with_name("test.html")


class AnalyzeRequest(BaseModel):
    text: str


class ReelRequest(BaseModel):
    url: str | None = None
    media_url: str | None = None       # Graph API media_url 있으면 CDN 직다운(yt-dlp 안 씀)
    thumbnail_url: str | None = None   # 영상 취득 실패 시 폴백


@app.post("/analyze")
def do_analyze(req: AnalyzeRequest) -> dict:
    return engine.analyze(req.text)


@app.post("/reel")
def do_reel(req: ReelRequest) -> dict:
    """릴스 URL → 취득 → STT/OCR → 분석. 무저장."""
    try:
        return pipeline.run(url=req.url, media_url=req.media_url, thumbnail_url=req.thumbnail_url)
    except Exception as e:
        logging.error("reel 실패: %s\n%s", e, traceback.format_exc())
        # 원인을 500 본문에 실어 Java 로그에서 바로 보이게 한다.
        raise HTTPException(status_code=500, detail=f"{type(e).__name__}: {e}") from e


@app.get("/")
def index() -> FileResponse:
    return FileResponse(_PAGE)


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8900)
