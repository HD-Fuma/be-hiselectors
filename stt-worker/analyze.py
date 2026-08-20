"""로컬(LLM 0) 정성 필드: 키워드 · 카테고리 · 욕설혐오.
- 키워드: kiwi 명사구 후보 → 임베딩 cosine 랭킹, 카테고리: zero-shot cosine (ko-sroberta 공유)
- 욕설혐오: 혐오/욕설 분류 모델(kor_unsmile) — 욕설 목록을 코드에 두지 않는다
스타일 · 톤 · 요약 · 강점 · 넓은 의미의 위험(정치/광고/건강 등)은 LLM 계층에서 처리(여기 없음).
transcript 텍스트만 입력 — YouTube(Gemini) 든 Instagram(whisper) 든 동일."""

from __future__ import annotations

import os
from functools import lru_cache

from kiwipiepy import Kiwi
from sentence_transformers import SentenceTransformer, util
from transformers import pipeline

import enums

_NOUN_TAGS = {"NNG", "NNP", "SL", "SN"}  # 일반/고유명사, 외국어, 숫자

MODEL_NAME = "jhgan/ko-sroberta-multitask"       # 한국어 문장 임베딩
HATE_MODEL = "smilegate-ai/kor_unsmile"          # 한국어 혐오/욕설 다중분류
MIN_CONFIDENCE = 0.30                            # 카테고리 zero-shot 임계
HATE_THRESHOLD = 0.5                             # 욕설/혐오 라벨 채택 임계
# ponytail: kor_unsmile 은 512 토큰 한계. 위험은 아웃라이어(한 구간)라 전체를 청크로 훑어
#           라벨별 최댓값을 취한다. 청크 크기는 노브.
CHUNK_CHARS = 300


@lru_cache(maxsize=1)
def _model() -> SentenceTransformer:
    return SentenceTransformer(MODEL_NAME)


@lru_cache(maxsize=1)
def _hate():
    return pipeline("text-classification", model=HATE_MODEL, top_k=None)


@lru_cache(maxsize=1)
def _kiwi() -> Kiwi:
    return Kiwi()


# 콘텐츠 대표 키워드로 의미 없는 초일반 명사. 필요하면 추가하는 노브.
_STOP = {"느낌", "요즘", "정도", "오늘", "이번", "다음", "생각", "이야기", "사람", "부분", "때문", "경우"}


def _noun_candidates(text: str) -> list[str]:
    """단일 명사구 후보. 조사·어미 제거. kiwi가 과분할한 복합어는 원문 위치가 붙어있으면 재결합
    (발레코어룩 등). 인접 어절을 억지로 잇는 bigram은 만들지 않는다(중복·노이즈 방지)."""
    words: list[str] = []
    cur, cur_end = "", -1
    for t in _kiwi().tokenize(text):
        if t.tag in _NOUN_TAGS or (t.tag == "XSN" and cur):  # XSN(력/성 등)은 앞 명사에 붙임
            if cur and t.start == cur_end:   # 원문에서 붙어있음 → 복합어
                cur += t.form
            else:
                if cur:
                    words.append(cur)
                cur = t.form
            cur_end = t.start + len(t.form)
        elif cur:
            words.append(cur)
            cur, cur_end = "", -1
    if cur:
        words.append(cur)
    words = [w for w in words if len(w) >= 2 and w not in _STOP]
    return list(dict.fromkeys(words))                        # 순서 유지·중복 제거


def keywords(text: str, top_n: int = 8) -> list[str]:
    """kiwi 단일 명사구 후보를 문서와의 임베딩 cosine으로 랭킹. 조사 붙은 명사도 안 잘리고,
    억지 bigram이 없어 중복/겹침이 안 생긴다."""
    if not text.strip():
        return []
    cands = _noun_candidates(text)
    if not cands:
        return []
    doc = _model().encode(text, convert_to_tensor=True)
    cand_emb = _model().encode(cands, convert_to_tensor=True)
    scores = util.cos_sim(doc, cand_emb)[0]
    order = scores.argsort(descending=True)[:top_n]
    return [cands[int(i)] for i in order]


def category(text: str) -> dict:
    """zero-shot 최근접 카테고리(공식 9코드). 임계 미만이면 라벨 비움(해당없음)."""
    if not text.strip():
        return {"label": "", "score": 0.0, "uncertain": True}
    labels = list(enums.CATEGORY)
    doc = _model().encode(text, convert_to_tensor=True)
    anc = _model().encode([enums.CATEGORY[c] for c in labels], convert_to_tensor=True)
    scores = util.cos_sim(doc, anc)[0]
    i = int(scores.argmax())
    score = round(float(scores[i]), 3)
    uncertain = score < MIN_CONFIDENCE
    return {"label": "" if uncertain else labels[i], "score": score, "uncertain": uncertain}


def _chunks(text: str) -> list[str]:
    return [text[i:i + CHUNK_CHARS] for i in range(0, len(text), CHUNK_CHARS)] or [""]


def hate(text: str) -> dict:
    """욕설·혐오 스크리닝(kor_unsmile). 전체를 청크로 훑어 라벨별 최댓값 → 임계 넘으면 '의심'.
    최종 판정이 아니다: 이 모델은 여성/성소수자 등 '화제로 다루기만 해도' 오탐이 잦다
    (예: 패션·섹시함 언급). 그래서 suspected 는 최종이 아니라 LLM 확인 대상.
    또 '욕설/혐오'만 본다 — 정치/광고/건강 등 넓은 위험은 LLM 계층 몫."""
    if not text.strip():
        return {"labels": [], "scores": {}, "suspected": False}
    agg: dict[str, float] = {}
    for chunk in _chunks(text):
        for d in _hate()(chunk, truncation=True)[0]:
            agg[d["label"]] = max(agg.get(d["label"], 0.0), float(d["score"]))
    hits = {label: round(s, 3) for label, s in agg.items()
            if label != "clean" and s >= HATE_THRESHOLD}
    # suspected → LLM이 맥락 확인해 확정/기각. 모델 단독으로 '혐오'라 단정하지 않는다.
    return {"labels": list(hits), "scores": hits, "suspected": bool(hits)}


def analyze(text: str, include_hate: bool = False) -> dict:
    """로컬 필드(키워드·카테고리). 욕설혐오는 Instagram 등 LLM을 안 태우는 소스에서만 필요해
    include_hate=True 일 때만 kor_unsmile을 돌린다. YouTube는 Gemini insight의 hateConfirmed를
    쓰므로 여기서 중복 실행하지 않는다."""
    result = {
        "keywords": keywords(text),
        "category": category(text),
    }
    if include_hate:
        result["hate"] = hate(text)
    return result


def _selfcheck() -> None:
    out = analyze("오늘은 신상 쿠션 파운데이션 발색이랑 지속력을 리뷰해볼게요. 언박싱부터 실사용까지.")
    assert out["category"]["label"] == "BEAUTY", out["category"]
    assert out["keywords"], "키워드가 비었음"
    assert "hate" not in out, "기본은 욕설혐오 안 돌려야 함"
    assert analyze("정상 텍스트", include_hate=True)["hate"]["suspected"] is False, "opt-in hate 실패"
    # 도메인 밖(뉴스) → 카테고리 라벨 비움
    off = analyze("오늘 여덟시 뉴스를 마치겠습니다 고맙습니다")
    assert off["category"]["label"] == "" and off["category"]["uncertain"], off["category"]
    assert analyze("")["category"]["label"] == "", "빈 입력 처리 실패"
    probe = os.environ.get("HATE_PROBE")
    if probe:
        assert hate(probe)["suspected"], "HATE_PROBE 가 suspected 로 안 잡힘"
    print("ok:", out)


if __name__ == "__main__":
    _selfcheck()
