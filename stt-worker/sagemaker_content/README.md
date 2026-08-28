# 콘텐츠 검수 전용 SageMaker STT

지원서용 `stt-worker/sagemaker` 응답 계약을 변경하지 않고 타임스탬프 세그먼트를 반환한다.

응답 예시:

```json
{
  "language": "ko",
  "segments": [
    {
      "segmentId": "stt-001",
      "startMs": 1200,
      "endMs": 3400,
      "text": "전사된 발화"
    }
  ]
}
```

워커 환경변수:

- `CONTENT_STT_BACKEND=sagemaker`
- `CONTENT_STT_ENDPOINT=content-whisper-large-v3-async`
- `CONTENT_STT_S3_BUCKET`
- `AWS_REGION`

`python package.py`로 별도 S3 아티팩트를 업로드한 뒤 `SAGEMAKER_ROLE_ARN`을
설정하고 `python deploy.py`를 실행한다. 기존 지원서용 모델과 엔드포인트는 변경하지 않는다.
