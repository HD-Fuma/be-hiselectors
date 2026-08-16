"""콘텐츠 정성 지표 중 로컬(LLM 0) 필드: 키워드 · 카테고리 · 콘텐츠 스타일.
임베딩 모델 하나로 셋 다 처리한다. KeyBERT(키워드) + zero-shot cosine(카테고리/스타일).
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


def _nearest(text: str, anchors: dict[str, str]) -> tuple[str, float]:
    labels = list(anchors)
    doc = _model().encode(text, convert_to_tensor=True)
    anc = _model().encode([anchors[l] for l in labels], convert_to_tensor=True)
    scores = util.cos_sim(doc, anc)[0]
    i = int(scores.argmax())
    return labels[i], round(float(scores[i]), 3)


def analyze(text: str) -> dict:
    """LLM 없이 되는 3필드. 카테고리는 여기서 영상별로 뽑고 최종은 크리에이터 단위로 집계."""
    cat, cat_score = _nearest(text, enums.CATEGORY) if text.strip() else ("", 0.0)
    style, style_score = _nearest(text, enums.STYLE) if text.strip() else ("", 0.0)
    return {
        "keywords": keywords(text),
        "category": {"label": cat, "score": cat_score,
                     "uncertain": cat_score < MIN_CONFIDENCE},
        "content_style": {"label": style, "score": style_score,
                          "uncertain": style_score < MIN_CONFIDENCE},
    }


def _selfcheck() -> None:
    text = "오늘은 신상 쿠션 파운데이션 발색이랑 지속력을 리뷰해볼게요. 언박싱부터 실사용까지."
    out = analyze(text)
    assert out["category"]["label"] == "BEAUTY", out["category"]
    assert out["content_style"]["label"] == "리뷰언박싱", out["content_style"]
    assert out["keywords"], "키워드가 비었음"
    assert analyze("")["category"]["label"] == "", "빈 입력 처리 실패"
    print("ok:", out)


if __name__ == "__main__":
    _selfcheck()
