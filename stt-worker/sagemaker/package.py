"""faster-whisper 모델을 버전별 SageMaker 아티팩트로 패키징한다.

기존 ``whisper/model.tar.gz``를 덮어쓰지 않는다. 업로드 결과는
``build/release.json``에 기록하며 deploy.py가 이 manifest를 읽는다.

사용 예시::

    python package.py hi-selectors-stt
    python package.py hi-selectors-stt --release-id 20260829-153000
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import tarfile

import boto3
from botocore.exceptions import ClientError
from huggingface_hub import snapshot_download


HERE = Path(__file__).resolve().parent
BUILD = HERE / "build"
WEIGHTS = BUILD / "models" / "large-v3"
TAR = HERE / "model.tar.gz"
DEFAULT_MANIFEST = BUILD / "release.json"
MODEL_REPOSITORY = "Systran/faster-whisper-large-v3"


def _default_release_id() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")


def _release_id(value: str) -> str:
    value = value.strip()
    if not re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,29}[A-Za-z0-9])?", value):
        raise argparse.ArgumentTypeError(
            "release-id는 영문자·숫자·하이픈만 사용하고 1~31자로 입력해야 합니다."
        )
    return value


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="구조화 faster-whisper 모델을 버전별 S3 경로에 업로드합니다."
    )
    parser.add_argument(
        "bucket",
        nargs="?",
        default=os.environ.get("STT_S3_BUCKET", "hi-selectors-stt"),
    )
    parser.add_argument(
        "--release-id",
        type=_release_id,
        default=_release_id(os.environ.get("STT_RELEASE_ID", _default_release_id())),
    )
    parser.add_argument(
        "--s3-key",
        default=os.environ.get("STT_MODEL_S3_KEY"),
        help="생략 시 whisper/models/<release-id>/model.tar.gz",
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path(os.environ.get("STT_RELEASE_MANIFEST", DEFAULT_MANIFEST)),
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="같은 S3 key가 이미 있을 때 덮어쓴다(배포된 release에는 사용 금지).",
    )
    return parser.parse_args()


def _object_exists(client, bucket: str, key: str) -> bool:
    try:
        client.head_object(Bucket=bucket, Key=key)
        return True
    except ClientError as error:
        code = error.response.get("Error", {}).get("Code")
        if code in {"404", "NoSuchKey", "NotFound"}:
            return False
        raise


def main() -> None:
    args = _arguments()
    region = os.environ.get("AWS_REGION", "ap-northeast-2")
    key = (
        args.s3_key or f"whisper/models/{args.release_id}/model.tar.gz"
    ).lstrip("/")
    if not key.endswith(".tar.gz"):
        raise ValueError("S3 모델 key는 .tar.gz로 끝나야 합니다.")
    s3 = boto3.client("s3", region_name=region)
    if _object_exists(s3, args.bucket, key) and not args.force:
        raise FileExistsError(
            f"S3 아티팩트가 이미 존재합니다: s3://{args.bucket}/{key}. "
            "새 release-id를 사용하세요."
        )

    print("1) large-v3 가중치 다운로드/확인 (~1.5GB)...")
    snapshot_download(
        MODEL_REPOSITORY,
        local_dir=WEIGHTS,
        allow_patterns=["*.bin", "*.json", "*.txt", "*.model", "vocabulary*"],
    )

    print("2) model.tar.gz 패키징...")
    with tarfile.open(TAR, "w:gz") as archive:
        archive.add(HERE / "inference.py", arcname="code/inference.py")
        archive.add(HERE / "requirements.txt", arcname="code/requirements.txt")
        archive.add(WEIGHTS, arcname="models/large-v3")

    model_data_url = f"s3://{args.bucket}/{key}"
    print(f"3) 버전 아티팩트 업로드: {model_data_url}")
    s3.upload_file(
        str(TAR),
        args.bucket,
        key,
        ExtraArgs={"ContentType": "application/gzip"},
    )

    manifest = {
        "schemaVersion": "1.0",
        "releaseId": args.release_id,
        "region": region,
        "bucket": args.bucket,
        "key": key,
        "modelDataUrl": model_data_url,
        "modelRepository": MODEL_REPOSITORY,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"완료: {model_data_url}")
    print(f"배포 manifest: {args.manifest.resolve()}")


if __name__ == "__main__":
    main()
