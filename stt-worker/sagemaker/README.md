# whisper large-v3 SageMaker Async 배포

유휴 시 GPU 0원(scale-to-zero), 요청 올 때만 켜져 전사하는 비동기 STT 엔드포인트.

## 파일
- `inference.py` — 추론 핸들러(faster-whisper large-v3, 로컬 가중치 로드)
- `requirements.txt` — 컨테이너 추가 설치(faster-whisper)
- `package.py` — large-v3 가중치를 받아 model.tar.gz(가중치 구움)로 묶어 S3 업로드
- `deploy.py` — 엔드포인트 생성 + scale-to-zero 오토스케일 (boto3, sagemaker SDK 불필요)
- `client.py` — 워커→엔드포인트 호출(오디오만 추출해 전송)

## 순서

```bash
# 0) 사전: GPU 쿼터(ml.g4dn.xlarge for endpoint usage) 승인, S3 버킷, SageMaker 실행 role
../.venv/Scripts/python.exe -m pip install boto3 huggingface_hub  # package.py 가 가중치 받을 때 필요

# 1) 가중치 구운 model.tar.gz 패키징 + 업로드 (~2.9GB, 몇 분)
../.venv/Scripts/python.exe package.py <BUCKET>

# 2) deploy.py 의 BUCKET / ROLE 확인하고 배포 (InService 까지 대기)
../.venv/Scripts/python.exe deploy.py

# 3) 검증(콜드스타트 몇 분)
aws s3 cp sample.mp3 s3://<BUCKET>/whisper/input/test.mp3
aws sagemaker-runtime invoke-endpoint-async \
  --endpoint-name whisper-large-v3-async \
  --input-location s3://<BUCKET>/whisper/input/test.mp3 \
  --content-type audio/mpeg    # 응답의 OutputLocation(S3)에 결과가 비동기로 떨어짐
aws s3 ls s3://<BUCKET>/whisper/output/   # 결과 JSON 확인
```

## 워커 연동
`media_stt.stt()` 가 `STT_BACKEND=sagemaker` 일 때 `client.stt(path)` 로 오프로드(이미 반영됨).
필요 env: `STT_BACKEND=sagemaker`, `STT_S3_BUCKET`, `STT_ENDPOINT`(기본 whisper-large-v3-async), `AWS_REGION`.
client 는 영상에서 오디오만 추출해(413 회피) 전송한다.

## 성능/비용
- Warm 추론 ~14초/릴스. 유휴 5분 후 scale-to-zero → GPU 0원.
- 콜드스타트: model.tar.gz(가중치 구움, ~2.9GB) S3 다운로드 + pip install ~3~4분(HF 재다운로드 없음).
- 콜드 더 줄이려면 커스텀 Docker 이미지에 deps+가중치까지 구워 ECR 사용.
