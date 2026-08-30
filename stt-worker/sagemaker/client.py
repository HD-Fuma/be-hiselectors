"""지원서와 콘텐츠 검수가 공유하는 SageMaker Async STT 클라이언트.

영상에서 오디오만 추출해 전송하고 공통 구조화 응답을 그대로 반환한다. 지원서용
문자열 평탄화는 media_stt가, 콘텐츠 검수용 정규화는 content_media_extraction이
각각 담당한다.
"""
import json
import os
import tempfile
import time
import uuid

import boto3

from stt_contract import SCHEMA_VERSION

REGION = os.environ.get("AWS_REGION", "ap-northeast-2")
BUCKET = os.environ.get("STT_S3_BUCKET", "hi-selectors-stt")
ENDPOINT = os.environ.get("STT_ENDPOINT", "whisper-large-v3-async")

_s3 = boto3.client("s3", region_name=REGION)
_smr = boto3.client("sagemaker-runtime", region_name=REGION)


def _extract_audio(media_path: str) -> str:
    """영상/미디어에서 오디오 스트림만 .m4a 로 복사(재인코딩 없음, 작고 빠름). 오디오 없으면 None."""
    import av
    fd, out_path = tempfile.mkstemp(suffix=".m4a")
    os.close(fd)  # Windows: 열린 fd가 남으면 파일 락 → 닫고 av가 쓰게 한다
    with av.open(media_path) as inp:
        if not inp.streams.audio:
            os.remove(out_path)  # 오디오 없으면 빈 임시파일 안 남기고 정리
            return None
        in_stream = inp.streams.audio[0]
        with av.open(out_path, "w") as out:
            out_stream = out.add_stream_from_template(in_stream)
            for packet in inp.demux(in_stream):
                if packet.dts is None:
                    continue
                packet.stream = out_stream
                out.mux(packet)
    return out_path


def stt(media_path: str, timeout: int = 580, poll: float = 3.0) -> dict:
    audio_path = _extract_audio(media_path)
    if audio_path is None:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "language": "",
            "audio": {"durationMs": None, "durationAfterVadMs": None},
            "segments": [],
        }

    try:
        key = f"whisper/input/{uuid.uuid4().hex}.m4a"
        _s3.upload_file(audio_path, BUCKET, key)
    finally:
        os.remove(audio_path)

    resp = _smr.invoke_endpoint_async(
        EndpointName=ENDPOINT,
        InputLocation=f"s3://{BUCKET}/{key}",
        ContentType="audio/mp4",
    )
    out_key = resp["OutputLocation"].split(f"{BUCKET}/", 1)[1]
    # 추론 실패는 OutputLocation 에 안 써지고 FailureLocation 에 써진다(deploy 의 S3FailurePath).
    # 둘 다 폴링해 실패면 즉시 예외 — 콜드스타트를 성공으로 오인해 580초 태우지 않는다.
    fail_key = resp.get("FailureLocation", "").split(f"{BUCKET}/", 1)[-1] or None

    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            body = _s3.get_object(Bucket=BUCKET, Key=out_key)["Body"].read()
            return json.loads(body)
        except _s3.exceptions.NoSuchKey:
            pass  # 아직 처리 중(콜드스타트 포함)
        if fail_key:
            try:
                err = _s3.get_object(Bucket=BUCKET, Key=fail_key)["Body"].read()
                raise RuntimeError(f"SageMaker STT 추론 실패: {err[:500]!r}")
            except _s3.exceptions.NoSuchKey:
                pass
        time.sleep(poll)
    raise TimeoutError(f"STT 결과 대기 초과: {out_key}")
