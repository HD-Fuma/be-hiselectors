"""로컬(LLM 0) 정성 필드: 키워드 · 카테고리.
- 키워드: kiwi 명사구 후보 → 임베딩 cosine 랭킹, 카테고리: zero-shot cosine (ko-sroberta 공유)
스타일 · 톤 · 요약 · 강점 · 넓은 의미의 위험(정치/광고/건강)·욕설혐오는 여기서 안 한다(다른 단계·담당).
transcript 텍스트만 입력 — YouTube(Gemini) 든 Instagram(whisper) 든 동일."""
from __future__ import annotations

from functools import lru_cache

from kiwipiepy import Kiwi
from sentence_transformers import SentenceTransformer, util

import enums

_NOUN_TAGS = frozenset({"NNG", "NNP", "SL", "SN"})
MODEL_NAME = "jhgan/ko-sroberta-multitask"
MIN_CONFIDENCE = 0.3
_STOP = frozenset({
    "요즘", "이번", "오늘", "때문", "이야기", "다음",
    "부분", "느낌", "생각", "경우", "사람", "정도",
})


@lru_cache(maxsize=1)
def _model() -> SentenceTransformer:
    return SentenceTransformer(MODEL_NAME)


@lru_cache(maxsize=1)
def _kiwi() -> Kiwi:
    return Kiwi()


def _noun_candidates(text: str) -> list[str]:
    """단일 명사구 후보. 조사·어미 제거. kiwi가 과분할한 복합어는 원문 위치가 붙어있으면 재결합
    (발레코어룩 등). 인접 어절을 억지로 잇는 bigram은 만들지 않는다(중복·노이즈 방지)."""
    words = []
    cur, cur_end = "", -1
    for t in _kiwi().tokenize(text):
        if t.tag in _NOUN_TAGS or (t.tag == "XSN" and cur):
            if cur and t.start == cur_end:
                cur += t.form
            else:
                if cur:
                    words.append(cur)
                cur = t.form
            cur_end = t.start + len(t.form)
        else:
            if cur:
                words.append(cur)
                cur, cur_end = "", -1
    if cur:
        words.append(cur)
    words = [w for w in words if len(w) >= 2 and w not in _STOP]
    return list(dict.fromkeys(words))


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


def analyze(text: str) -> dict:
    """로컬 필드(키워드·카테고리). 욕설혐오·위험 판단은 여기서 안 한다."""
    return {
        "keywords": keywords(text),
        "category": category(text),
    }


def _selfcheck() -> None:
    beauty = analyze("오늘은 신상 쿠션 파운데이션 발색이랑 지속력을 리뷰해볼게요. 언박싱부터 실사용까지.")
    assert beauty["category"]["label"] == "BEAUTY", beauty["category"]
    assert beauty["keywords"], "키워드가 비었음"
    assert "hate" not in beauty, "hate 는 더 이상 산출하지 않음"

    news = analyze("오늘 여덟시 뉴스를 마치겠습니다 고맙습니다")
    assert news["category"]["label"] == "" and analyze("")["category"]["uncertain"], "빈 입력 처리 실패"

    print("ok:", beauty["keywords"])


if __name__ == "__main__":
    _selfcheck()
