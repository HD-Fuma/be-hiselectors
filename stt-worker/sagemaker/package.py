"""large-v3 ct2 가중치를 받아 code/ + models/large-v3/ 를 model.tar.gz 로 묶어 S3 업로드.
가중치를 구워두면 콜드스타트마다 HF 1.5GB 재다운로드가 사라진다.

  ../.venv/Scripts/python.exe package.py hi-selectors-stt
"""
import os
import subprocess
import sys
import tarfile

from huggingface_hub import snapshot_download

BUCKET = sys.argv[1] if len(sys.argv) > 1 else "hi-selectors-stt"
HERE = os.path.dirname(os.path.abspath(__file__))
WEIGHTS = os.path.join(HERE, "build", "models", "large-v3")
TAR = os.path.join(HERE, "model.tar.gz")

print("1) large-v3 가중치 다운로드 (~1.5GB)...")
snapshot_download(
    "Systran/faster-whisper-large-v3",
    local_dir=WEIGHTS,
    allow_patterns=["*.bin", "*.json", "*.txt", "*.model", "vocabulary*"],
)

print("2) model.tar.gz 패키징...")
with tarfile.open(TAR, "w:gz") as tar:
    tar.add(os.path.join(HERE, "inference.py"), arcname="code/inference.py")
    tar.add(os.path.join(HERE, "requirements.txt"), arcname="code/requirements.txt")
    tar.add(WEIGHTS, arcname="models/large-v3")

print("3) S3 업로드...")
subprocess.run(["aws", "s3", "cp", TAR, f"s3://{BUCKET}/whisper/model.tar.gz"], check=True)
print(f"done → s3://{BUCKET}/whisper/model.tar.gz")
