# 공통 구조화 whisper large-v3 SageMaker Async 배포

유휴 시 GPU 0원(scale-to-zero), 요청 올 때만 켜져 전사하는 비동기 STT 엔드포인트.

## 파일
- `inference.py` — 추론 핸들러(faster-whisper large-v3, 로컬 가중치 로드)
- `requirements.txt` — 컨테이너 추가 설치(faster-whisper)
- `package.py` — large-v3 가중치를 받아 버전별 S3 경로에 업로드하고
  `build/release.json` 생성
- `deploy.py` — 새 Model/EndpointConfig를 만들고 기존 엔드포인트는
  `UpdateEndpoint`로 무중단 교체(없으면 신규 생성)
- `client.py` — 워커→엔드포인트 호출(오디오만 추출해 전송)

## 순서

```bash
# 0) 사전: GPU 쿼터(ml.g4dn.xlarge for endpoint usage) 승인, S3 버킷, SageMaker 실행 role
../.venv/Scripts/python.exe -m pip install boto3 huggingface_hub  # package.py 가 가중치 받을 때 필요

# 1) 가중치 구운 model.tar.gz 패키징 + 버전별 업로드 (~2.9GB, 몇 분)
#    기본 경로: s3://<BUCKET>/whisper/models/<UTC timestamp>/model.tar.gz
../.venv/Scripts/python.exe package.py <BUCKET>

# 2) build/release.json의 아티팩트를 기존 엔드포인트에 배포
#    기존 엔드포인트의 실행 role/image/instance/output 경로를 자동으로 이어받는다.
$env:STT_ENDPOINT="whisper-large-v3-async"
../.venv/Scripts/python.exe deploy.py --dry-run
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
`media_stt.stt()` 가 `STT_BACKEND=sagemaker` 일 때 `client.stt(path)` 로 오프로드한다.
SageMaker의 구조화 세그먼트 응답은 이 클라이언트에서 지원서용 단일 문자열로 평탄화한다.
필요 env: `STT_BACKEND=sagemaker`, `STT_S3_BUCKET`, `STT_ENDPOINT`(기본
`whisper-large-v3-async`), `AWS_REGION`.
client 는 영상에서 오디오만 추출해(413 회피) 전송한다.

## 배포 안전장치

- 운영 중인 `whisper/model.tar.gz`를 덮어쓰지 않고 release별 경로를 사용한다.
- Model과 EndpointConfig도 release별 새 이름으로 생성한다.
- 기존 Endpoint는 삭제/재생성하지 않고 `UpdateEndpoint`로 교체한다.
- 기존 EndpointConfig는 자동 삭제하지 않는다.
- 배포 결과와 이전 EndpointConfig는 `build/deploy-result.json`에 남기며,
  배포 완료 시 롤백 명령도 출력한다.
- 같은 manifest로 배포를 다시 실행하면 동일한 Model/EndpointConfig를 재사용한다.
- 패키징은 같은 release의 S3 파일이 있으면 기본적으로 중단한다. 아직 배포하지 않은
  손상된 업로드를 의도적으로 교체할 때만 `package.py --force`를 사용한다.

release를 명시하려면 다음처럼 실행한다.

```powershell
python package.py hi-selectors-stt --release-id 20260829-153000
python deploy.py --endpoint whisper-large-v3-async
```

신규 엔드포인트를 만드는 경우에만 `SAGEMAKER_ROLE_ARN`이 필수다. 기존
엔드포인트 업데이트는 현재 Model의 실행 role을 자동으로 사용한다.

## 성능/비용
- Warm 추론 ~14초/릴스. 유휴 5분 후 scale-to-zero → GPU 0원.
- 콜드스타트: model.tar.gz(가중치 구움, ~2.9GB) S3 다운로드 + pip install ~3~4분(HF 재다운로드 없음).
- 콜드 더 줄이려면 커스텀 Docker 이미지에 deps+가중치까지 구워 ECR 사용.
