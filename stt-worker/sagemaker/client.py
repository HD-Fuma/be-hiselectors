"""워커(CPU)에서 SageMaker Async 엔드포인트로 STT 요청. media_stt.stt() 가 이걸 호출하도록 교체한다.
흐름: 영상에서 오디오만 추출(작음) → S3 업로드 → invoke_endpoint_async → 출력 S3 폴링 → transcript.
영상 통째 전송은 컨테이너 요청 한도(413)를 넘으므로 오디오만 보낸다. STT엔 오디오면 충분.
콜드스타트(0→1)+최초 모델 다운로드 시 첫 요청은 수 분 걸릴 수 있음(이후 빠름)."""
import json
import os
import tempfile
import time
import uuid

import boto3

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


def stt(media_path: str, timeout: int = 580, poll: float = 3.0) -> str:
    audio_path = _extract_audio(media_path)
    if audio_path is None:
        return ""  # 오디오 없음(무음 영상 등)

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
            return json.loads(body).get("stt", "")
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
