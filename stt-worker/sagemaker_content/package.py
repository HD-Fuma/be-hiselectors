"""콘텐츠 검수 전용 모델 아티팩트를 패키징해 별도 S3 키에 업로드한다.

사용법: python package.py [bucket]
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

snapshot_download(
    "Systran/faster-whisper-large-v3",
    local_dir=WEIGHTS,
    allow_patterns=["*.bin", "*.json", "*.txt", "*.model", "vocabulary*"],
)

with tarfile.open(TAR, "w:gz") as archive:
    archive.add(os.path.join(HERE, "inference.py"), arcname="code/inference.py")
    archive.add(os.path.join(HERE, "requirements.txt"), arcname="code/requirements.txt")
    archive.add(WEIGHTS, arcname="models/large-v3")

target = f"s3://{BUCKET}/content-whisper/model.tar.gz"
subprocess.run(["aws", "s3", "cp", TAR, target], check=True)
print(f"uploaded: {target}")
