"""Instagram 미디어 → transcript(음성 STT + 화면 OCR). analyze 로 넘길 텍스트를 만든다.
- 음성: faster-whisper large-v3 + VAD (숏폼 BGM-only 구간의 whisper 환각 방지)
- 화면텍스트: N초 간격 프레임 샘플 → RapidOCR → 라인 중복 제거(정적 오버레이가 여러 프레임에
  반복되므로 텍스트 단위로 dedupe)
영상이면 음성+화면 둘 다, 이미지(저작권 릴스 → 썸네일 폴백)면 OCR만(음성 빈 값)."""
from __future__ import annotations

import logging
import os
from functools import lru_cache

from stt_contract import transcript_text

# 기본 large-v3. RAM 부족 머신은 OOM 시 아래 체인으로 자동 강등(STT_MODEL 로 시작점만 바꿈).
STT_MODEL = os.environ.get("STT_MODEL", "large-v3")
_SIZES = ["large-v3", "medium", "small", "tiny"]  # 큰→작은 순. OOM 폴백 사다리
SAMPLE_EVERY = 1.5  # 초. 프레임 OCR 샘플 간격
MIN_OCR_SCORE = 0.6  # 이 신뢰도 미만 인식라인은 버린다(스타일폰트/모션블러 깨진 텍스트 제거)
IMAGE_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def _is_oom(e: Exception) -> bool:
    m = str(e).lower()
    return isinstance(e, MemoryError) or any(k in m for k in ("malloc", "memory", "alloc", "bad_alloc"))


@lru_cache(maxsize=1)
def _whisper():
    from faster_whisper import WhisperModel
    # CPU int8: 홈IP·무료·오프라인. GPU면 device="cuda", compute_type="float16".
    # STT_MODEL 부터 시도하고, RAM 부족(mkl_malloc 등)이면 더 작은 모델로 자동 폴백.
    chain = _SIZES[_SIZES.index(STT_MODEL):] if STT_MODEL in _SIZES else [STT_MODEL, "small", "tiny"]
    last = None
    for name in chain:
        try:
            model = WhisperModel(name, device="cpu", compute_type="int8")
            if name != STT_MODEL:
                logging.warning("STT 모델 %s 로드 실패 → %s 로 폴백", STT_MODEL, name)
            return model
        except Exception as e:
            if not _is_oom(e):
                raise  # 메모리 외 오류(다운로드 등)는 폴백하지 않고 그대로 던진다
            logging.warning("STT 모델 %s OOM(%s), 더 작은 모델 시도", name, e)
            last = e
    raise RuntimeError(f"모든 STT 모델 로드 실패: {last}")


@lru_cache(maxsize=1)
def _ocr():
    # 기본 PP-OCRv6엔 한국어 사전이 없다 → v4 모바일 한국어 인식모델을 명시해야 한글이 잡힌다.
    from rapidocr import RapidOCR, LangRec, ModelType, OCRVersion
    return RapidOCR(params={
        "Rec.lang_type": LangRec.KOREAN,
        "Rec.ocr_version": OCRVersion.PPOCRV4,
        "Rec.model_type": ModelType.MOBILE,
    })


def _dedupe(lines: list[str]) -> list[str]:
    """공백 정리 후 순서보존 유니크. 빈 줄 제거."""
    return list(dict.fromkeys(s for s in (ln.strip() for ln in lines) if s))


def _has_audio(path: str) -> bool:
    """오디오 스트림 존재 여부. 무음 영상은 whisper decode_audio 가 IndexError 를 던지므로 선제 차단."""
    import av
    try:
        with av.open(path) as c:
            return bool(c.streams.audio)
    except Exception:
        return False


def stt(path: str) -> str:
    """음성 전사(한국어). STT_BACKEND=sagemaker 면 GPU 엔드포인트로 오프로드(오디오만 전송),
    아니면 로컬 faster-whisper. 워커 CPU는 취득·OCR만 지고 무거운 STT만 GPU로 뺄 수 있다.
    오디오 트랙이 없는 영상(무음 릴스 등)은 STT 를 건너뛰고 빈 값을 돌려준다(OCR 은 별도 수행)."""
    if not _has_audio(path):
        logging.info("오디오 스트림 없음 → STT 건너뜀: %s", path)
        return ""
    if os.environ.get("STT_BACKEND") == "sagemaker":
        from sagemaker import client  # stt-worker/sagemaker/client.py
        return transcript_text(client.stt(path))
    segments, _ = _whisper().transcribe(path, language="ko", vad_filter=True)
    return " ".join(seg.text.strip() for seg in segments).strip()


def _frames(path: str, every: float = SAMPLE_EVERY):
    """every 초 간격 프레임을 BGR ndarray로 내놓는다. faster-whisper가 끌고 온 av로 디코드.
    ponytail: 전 프레임 디코드 후 샘플. 느리면 seek 기반 샘플링으로 교체."""
    import av
    with av.open(path) as container:
        stream = container.streams.video[0]
        next_t = 0.0
        for frame in container.decode(stream):
            t = float(frame.pts * stream.time_base) if frame.pts is not None else next_t
            if t >= next_t:
                yield frame.to_ndarray(format="bgr24")
                next_t = t + every


def _ocr_lines(image) -> list[str]:
    """RapidOCR(v2): 경로 또는 ndarray → 인식된 텍스트 라인. 신뢰도 낮은 깨진 라인은 버린다."""
    res = _ocr()(image)
    if res is None or not res.txts:
        return []
    scores = res.scores or [1.0] * len(res.txts)
    return [t for t, s in zip(res.txts, scores) if float(s) >= MIN_OCR_SCORE]


def ocr_video(path: str) -> str:
    # ponytail: 움직이는 스타일폰트 프레임은 고신뢰 오인식 노이즈가 남는다. 정밀화 필요 시
    #   프레임 image-hash dedup + 안정(장면전환 후 정착)프레임 선별 + rec server 모델로 업그레이드.
    lines: list[str] = []
    for img in _frames(path):
        lines += _ocr_lines(img)
    return " ".join(_dedupe(lines))


def ocr_image(path: str) -> str:
    return " ".join(_dedupe(_ocr_lines(path)))


def transcribe(path: str) -> dict:
    """영상 → {stt, ocr}. 이미지(썸네일 폴백) → {stt:'', ocr}. analyze 입력용."""
    ext = os.path.splitext(path)[1].lower()
    if ext in IMAGE_EXT:
        return {"stt": "", "ocr": ocr_image(path)}
    return {"stt": stt(path), "ocr": ocr_video(path)}


def _selfcheck() -> None:
    # 모델 없이 순수 로직만: dedupe(공백·중복·빈줄 제거, 순서보존), 이미지 분기(음성 빈 값).
    assert _dedupe(["찌든때", "찌든때 ", " 클리너", "", "찌든때"]) == ["찌든때", "클리너"], "dedupe 실패"

    ext = os.path.splitext("cover.jpg")[1].lower()
    assert ext in IMAGE_EXT, "이미지 확장자 인식 실패"

    # 없는 파일/오디오 없는 파일은 크래시 대신 False (whisper IndexError 선제 차단).
    assert _has_audio("nonexistent.mp4") is False, "_has_audio 예외 처리 실패"

    # 실제 미디어가 있으면 end-to-end 까지(무거운 모델 다운로드는 이때만).
    #   OCR:  OCR_SAMPLE=<이미지/영상 경로>
    #   STT:  STT_SAMPLE=<영상/오디오 경로>
    sample = os.environ.get("OCR_SAMPLE")
    if sample:
        out = ocr_image(sample) if os.path.splitext(sample)[1].lower() in IMAGE_EXT else ocr_video(sample)
        assert out, "OCR 결과 없음"
        print("ocr:", out[:120].replace("\n", " | "))

    stt_sample = os.environ.get("STT_SAMPLE")
    if stt_sample:
        print("stt:", stt(stt_sample)[:120])

    print("ok")


if __name__ == "__main__":
    _selfcheck()
