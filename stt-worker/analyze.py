"""로컬(LLM 0) 정성 필드: 키워드 · 카테고리.
- 키워드: kiwi 명사구 후보 → 임베딩 cosine 랭킹, 카테고리: zero-shot cosine (ko-sroberta 공유)
스타일 · 톤 · 요약 · 강점 · 넓은 의미의 위험(정치/광고/건강)·욕설혐오는 여기서 안 한다(다른 단계·담당).
transcript 텍스트만 입력 — YouTube(Gemini) 든 Instagram(whisper) 든 동일."""
from __future__ import annotations
from functools import lru_cache
from kiwipiepy import Kiwi
from sentence_transformers import SentenceTransformer, util
import enums

# 한국어 명사만. SL(영문)·SN(숫자)은 OCR 워터마크·로고·UI 노이즈(0004, ROCKPINK, GV16 등)라 제외.
# ponytail: 영문 브랜드 키워드가 필요해지면 allowlist 로 선별 통과시키는 게 정석(전면 허용 X).
_NOUN_TAGS = frozenset({"NNG", "NNP"})
MODEL_NAME = "jhgan/ko-sroberta-multitask"
MIN_CONFIDENCE = 0.3
_MAX_JOIN = 3  # 재결합 허용 형태소 개수. 넘으면 run-on 으로 보고 버림. 정상/노이즈 경계라 튜닝값.
_STOP = frozenset({
    "요즘", "이번", "오늘", "때문", "이야기", "다음",
    "부분", "느낌", "생각", "경우", "사람", "정도",
    "감사", "안녕", "구독", "좋아요", "알림",  # 인사·유튜브 상투 필러
})


@lru_cache(maxsize=1)
def _model() -> SentenceTransformer:
    return SentenceTransformer(MODEL_NAME)


@lru_cache(maxsize=1)
def _kiwi() -> Kiwi:
    return Kiwi()


def _noun_candidates(text: str) -> list[str]:
    """단일 명사구 후보. 조사·어미 제거. kiwi가 과분할한 복합어는 원문 위치가 붙어있으면 재결합
    (발레코어룩 등). 인접 어절을 억지로 잇는 bigram은 만들지 않는다(중복·노이즈 방지).

    ponytail: 형태소 _MAX_JOIN 개까지만 재결합한다. STT 자막은 띄어쓰기가 없어
    '공깃밥반공기추가'처럼 명사 4~5개가 통째로 붙는 run-on 이 생기는데, 이걸 버린다.
    구조가 같은 '김치말이밥'(3형태소, 정상)은 살린다 → 이 값이 정상/노이즈 경계라 튜닝 지점.
    3형태소 애매어(먹방종료후)·STT 오탈자(함김치찌가)는 여기서 못 거른다(사전 필요)."""
    words = []
    cur, cur_end, parts = "", -1, 0
    for t in _kiwi().tokenize(text):
        if t.tag in _NOUN_TAGS or (t.tag == "XSN" and cur):
            if cur and t.start == cur_end:
                cur += t.form
                parts += 1
            else:
                if cur and parts <= _MAX_JOIN:
                    words.append(cur)
                cur, parts = t.form, 1
            cur_end = t.start + len(t.form)
        else:
            if cur and parts <= _MAX_JOIN:
                words.append(cur)
            cur, cur_end, parts = "", -1, 0
    if cur and parts <= _MAX_JOIN:
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

    # run-on 필터: 띄어쓰기 없는 명사 4개+ 결합은 버리고, 3형태소 정상 복합어는 남긴다.
    # (kiwi 형태소 분할이 가정과 다르면 _MAX_JOIN 을 조정. 아래 출력으로 실제 후보 확인)
    cands = _noun_candidates("공깃밥반공기추가 열무냉김치말이밥 김치말이밥 공깃밥")
    print("candidates:", cands)
    assert "공깃밥반공기추가" not in cands, cands
    assert "열무냉김치말이밥" not in cands, cands
    assert "김치말이밥" in cands, cands
    assert "공깃밥" in cands, cands

    # OCR 노이즈(영문·숫자 워터마크/로고)는 키워드에서 제외한다.
    noise = _noun_candidates("ROCKPINK CosmeHongKong 0004 GV16 감사합니다 김치말이밥")
    print("noise-filtered:", noise)
    assert not any(c in noise for c in ["ROCKPINK", "CosmeHongKong", "0004", "GV16", "감사"]), noise
    assert "김치말이밥" in noise, noise

    print("ok:", beauty["keywords"])


if __name__ == "__main__":
    _selfcheck()
