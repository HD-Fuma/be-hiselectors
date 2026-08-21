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

import acquire
import analyze as engine
import pipeline

app = FastAPI(title="정성 엔진 테스트")
_PAGE = Path(__file__).with_name("test.html")


class AnalyzeRequest(BaseModel):
    text: str

class ReelRequest(BaseModel):
    media_url: str | None = None       # Graph API media_url(공식 API) — CDN 직다운
    thumbnail_url: str | None = None   # media_url 없을 때(저작권 릴스) 폴백


@app.post("/analyze")
def do_analyze(req: AnalyzeRequest) -> dict:
    return engine.analyze(req.text)


@app.post("/reel")
def do_reel(req: ReelRequest) -> dict:
    """Graph API media_url → 취득 → STT/OCR → 분석. 무저장."""
    try:
        return pipeline.run(media_url=req.media_url, thumbnail_url=req.thumbnail_url)
    except acquire.CdnExpiredError as e:
        # 만료는 재요청 대상 → 일반 실패(500)와 구분되게 410 로 명시. Java가 fresh URL 재요청.
        logging.warning("media_url 만료: %s", e)
        raise HTTPException(status_code=410, detail=f"CDN_EXPIRED: {e}") from e
    except acquire.AcquireError as e:
        # 소스 없음/스킴·호스트 불허 = 잘못된 요청(워커 장애 아님) → 422.
        logging.warning("취득 요청 오류: %s", e)
        raise HTTPException(status_code=422, detail=str(e)) from e
    except Exception as e:
        logging.error("reel 실패: %s\n%s", e, traceback.format_exc())
        # 원인을 500 본문에 실어 Java 로그에서 바로 보이게 한다.
        raise HTTPException(status_code=500, detail=f"{type(e).__name__}: {e}") from e


@app.get("/")
def index() -> FileResponse:
    return FileResponse(_PAGE)


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8900)
