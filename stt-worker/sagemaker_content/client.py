"""콘텐츠 검수 전용 SageMaker Async STT 클라이언트."""
import json
import os
import tempfile
import time
import uuid

import boto3

REGION = os.environ.get("AWS_REGION", "ap-northeast-2")
BUCKET = os.environ.get("CONTENT_STT_S3_BUCKET", "hi-selectors-stt")
ENDPOINT = os.environ.get("CONTENT_STT_ENDPOINT", "content-whisper-large-v3-async")

_s3 = boto3.client("s3", region_name=REGION)
_runtime = boto3.client("sagemaker-runtime", region_name=REGION)


def _extract_audio(media_path: str) -> str | None:
    import av
    fd, output_path = tempfile.mkstemp(suffix=".m4a")
    os.close(fd)
    with av.open(media_path) as source:
        if not source.streams.audio:
            os.remove(output_path)
            return None
        source_stream = source.streams.audio[0]
        with av.open(output_path, "w") as output:
            output_stream = output.add_stream_from_template(source_stream)
            for packet in source.demux(source_stream):
                if packet.dts is None:
                    continue
                packet.stream = output_stream
                output.mux(packet)
    return output_path


def stt(media_path: str, timeout: int = 580, poll: float = 3.0) -> dict:
    audio_path = _extract_audio(media_path)
    if audio_path is None:
        return {"language": "", "segments": []}
    key = f"content-whisper/input/{uuid.uuid4().hex}.m4a"
    try:
        _s3.upload_file(audio_path, BUCKET, key)
    finally:
        os.remove(audio_path)

    response = _runtime.invoke_endpoint_async(
        EndpointName=ENDPOINT,
        InputLocation=f"s3://{BUCKET}/{key}",
        ContentType="audio/mp4",
    )
    output_key = response["OutputLocation"].split(f"{BUCKET}/", 1)[1]
    failure_location = response.get("FailureLocation", "")
    failure_key = failure_location.split(f"{BUCKET}/", 1)[-1] if failure_location else None

    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            body = _s3.get_object(Bucket=BUCKET, Key=output_key)["Body"].read()
            return json.loads(body)
        except _s3.exceptions.NoSuchKey:
            pass
        if failure_key:
            try:
                error = _s3.get_object(Bucket=BUCKET, Key=failure_key)["Body"].read()
                raise RuntimeError(f"콘텐츠 SageMaker STT 실패: {error[:500]!r}")
            except _s3.exceptions.NoSuchKey:
                pass
        time.sleep(poll)
    raise TimeoutError(f"콘텐츠 STT 결과 대기 초과: {output_key}")
