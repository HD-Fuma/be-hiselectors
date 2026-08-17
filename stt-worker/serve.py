"""테스트용 로컬 서버. transcript 텍스트 → 정성 5필드 JSON.
실행: python serve.py  → http://127.0.0.1:8900 접속.
Java 없이 엔진만 단독으로 확인하는 용도."""

from pathlib import Path

import uvicorn
from fastapi import FastAPI
from fastapi.responses import FileResponse
from pydantic import BaseModel

import analyze as engine

app = FastAPI(title="정성 엔진 테스트")
_PAGE = Path(__file__).with_name("test.html")


class AnalyzeRequest(BaseModel):
    text: str


@app.post("/analyze")
def do_analyze(req: AnalyzeRequest) -> dict:
    return engine.analyze(req.text)


@app.get("/")
def index() -> FileResponse:
    return FileResponse(_PAGE)


if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8900)
