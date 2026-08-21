"""워커(CPU)에서 SageMaker Async 엔드포인트로 STT 요청. media_stt.stt() 가 이걸 호출하도록 교체한다.
흐름: 미디어 바이트 S3 업로드 → invoke_endpoint_async → 출력 S3 폴링 → transcript.
콜드스타트(0→1) 시 첫 요청은 수 분 걸릴 수 있음(이후 빠름) — 배치로 몰아 처리 권장."""
import json
import os
import time
import uuid

import boto3

REGION = os.environ.get("AWS_REGION", "ap-northeast-2")
BUCKET = os.environ["STT_S3_BUCKET"]
ENDPOINT = os.environ.get("STT_ENDPOINT", "whisper-large-v3-async")

_s3 = boto3.client("s3", region_name=REGION)
_smr = boto3.client("sagemaker-runtime", region_name=REGION)


def stt(media_path: str, timeout: int = 600, poll: float = 3.0) -> str:
    key = f"whisper/input/{uuid.uuid4().hex}{os.path.splitext(media_path)[1]}"
    _s3.upload_file(media_path, BUCKET, key)

    resp = _smr.invoke_endpoint_async(
        EndpointName=ENDPOINT,
        InputLocation=f"s3://{BUCKET}/{key}",
        ContentType="video/mp4",
    )
    out_key = resp["OutputLocation"].split(f"{BUCKET}/", 1)[1]

    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            body = _s3.get_object(Bucket=BUCKET, Key=out_key)["Body"].read()
            return json.loads(body).get("stt", "")
        except _s3.exceptions.NoSuchKey:
            time.sleep(poll)  # 아직 처리 중(콜드스타트 포함)
    raise TimeoutError(f"STT 결과 대기 초과: {out_key}")
