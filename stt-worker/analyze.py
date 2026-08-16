"""콘텐츠 정성 지표 중 로컬(LLM 0) 필드: 키워드 · 카테고리 · 스타일 · 톤 · 위험.
임베딩 모델 하나로 키워드/카테고리/스타일/톤 처리(KeyBERT + zero-shot cosine),
위험은 lexicon 스캔(아웃라이어라 임베딩 평균으론 못 잡음).
transcript 텍스트만 입력 — YouTube(Gemini) stt+ocr+summary 든 Instagram(whisper) 든 동일."""

from __future__ import annotations

from functools import lru_cache

from keybert import KeyBERT
from sentence_transformers import SentenceTransformer, util

import enums

MODEL_NAME = "jhgan/ko-sroberta-multitask"  # 한국어 문장 임베딩
MIN_CONFIDENCE = 0.30


@lru_cache(maxsize=1)
def _model() -> SentenceTransformer:
    return SentenceTransformer(MODEL_NAME)


@lru_cache(maxsize=1)
def _keybert() -> KeyBERT:
    return KeyBERT(model=_model())


def keywords(text: str, top_n: int = 8) -> list[str]:
    if not text.strip():
        return []
    pairs = _keybert().extract_keywords(
        text, keyphrase_ngram_range=(1, 2), top_n=top_n)
    return [kw for kw, _ in pairs]


def _classify(text: str, anchors: dict[str, str]) -> dict:
    """zero-shot 최근접 라벨 + 신뢰도. 빈 입력이면 빈 라벨."""
    if not text.strip():
        return {"label": "", "score": 0.0, "uncertain": True}
    labels = list(anchors)
    doc = _model().encode(text, convert_to_tensor=True)
    anc = _model().encode([anchors[l] for l in labels], convert_to_tensor=True)
    scores = util.cos_sim(doc, anc)[0]
    i = int(scores.argmax())
    score = round(float(scores[i]), 3)
    return {"label": labels[i], "score": score, "uncertain": score < MIN_CONFIDENCE}


def risk(text: str) -> dict:
    """lexicon 매칭. 걸리면 needs_llm=True(크루드하니 LLM로 확인), 안 걸리면 '없음'으로 스킵."""
    hits = {label: [t for t in terms if t in text]
            for label, terms in enums.RISK_LEXICON.items()}
    hits = {label: found for label, found in hits.items() if found}
    return {"labels": list(hits), "hits": hits, "needs_llm": bool(hits)}


def analyze(text: str) -> dict:
    """LLM 없이 되는 5필드. 카테고리는 영상별로 뽑고 최종은 크리에이터 단위로 집계.
    톤 uncertain / 위험 needs_llm 인 건만 상위에서 LLM 폴백."""
    return {
        "keywords": keywords(text),
        "category": _classify(text, enums.CATEGORY),
        "content_style": _classify(text, enums.STYLE),
        "tone": _classify(text, enums.TONE),
        "risk": risk(text),
    }


def _selfcheck() -> None:
    text = "오늘은 신상 쿠션 파운데이션 발색이랑 지속력을 리뷰해볼게요. 언박싱부터 실사용까지."
    out = analyze(text)
    assert out["category"]["label"] == "BEAUTY", out["category"]
    assert out["content_style"]["label"] == "리뷰언박싱", out["content_style"]
    assert out["keywords"], "키워드가 비었음"
    assert out["tone"]["label"], "톤 라벨이 비었음"
    assert out["risk"]["needs_llm"] is False, "정상 텍스트인데 위험 걸림"
    assert analyze("")["category"]["label"] == "", "빈 입력 처리 실패"
    # 위험 lexicon 동작 확인: 비속어 있으면 걸리고 needs_llm=True
    bad = risk("이건 진짜 존나 별로다")
    assert "비속어" in bad["labels"] and bad["needs_llm"], bad
    print("ok:", out)


if __name__ == "__main__":
    _selfcheck()
