# 지원자 분석 Fargate 작업

API 서버의 정기 분석을 `EventBridge Scheduler → Lambda → ECS Fargate`로 분리한다.
Lambda는 이미 실행 중인 작업이 없을 때만 Fargate 작업 하나를 시작한다. 작업은 지원자 한 명을
분석하고 종료한다.

## 최초 설정

Secrets Manager에 JSON 시크릿 하나를 만든다. 키는 다음과 같다.

```json
{
  "DB_HOST": "...",
  "DB_PORT": "3306",
  "DB_NAME": "hiselectors",
  "DB_USERNAME": "...",
  "DB_PASSWORD": "...",
  "JWT_SECRET": "...",
  "GEMINI_API_KEY": "...",
  "META_API_VERSION": "v24.0",
  "META_INSTAGRAM_BUSINESS_ACCOUNT_ID": "...",
  "META_LONG_LIVED_ACCESS_TOKEN": "..."
}
```

GitHub 저장소에 아래 값을 등록한다.

- Secret `ANALYSIS_RUNTIME_SECRET_ARN`: 위 Secrets Manager ARN
- Variable `ANALYSIS_SUBNET_IDS`: 쉼표로 구분한 서브넷 ID
- Variable `ANALYSIS_SECURITY_GROUP_IDS`: RDS 접근이 허용된 보안 그룹 ID
- Variable `ANALYSIS_ASSIGN_PUBLIC_IP`: public subnet이면 `ENABLED`, NAT가 있는 private subnet이면 `DISABLED`

`Deploy analysis worker`를 `schedule_state=DISABLED`로 먼저 실행한다. API/worker 이미지 태그가
현재 커밋 SHA와 다르면 배포된 태그를 `image_tag`에 넣는다.

## 원샷 검증

```bash
aws lambda invoke \
  --region ap-northeast-2 \
  --function-name hiselectors-analysis-dispatcher \
  /tmp/analysis-dispatch.json

aws logs tail /hiselectors/analysis-job \
  --region ap-northeast-2 \
  --since 10m \
  --follow
```

로그에서 `지원자 콘텐츠 분석 완료`와 task의 `STOPPED` 상태를 확인한다.

## 전환

원샷 검증 후 EC2 `/srv/hiselectors/.env`에 아래 값을 넣고 API를 재생성한다.

```dotenv
APPLICATION_CONTENT_ANALYSIS_SCHEDULER_ENABLED=false
```

그 다음 `Deploy analysis worker`를 `schedule_state=ENABLED`로 다시 실행한다. 이 순서로 해야
EC2 스케줄러와 Fargate 스케줄러가 동시에 Gemini를 호출하지 않는다.
