# 지원자 분석 Fargate 작업

미디어 수집이 끝나면 API가 FIFO SQS에 지원자 ID를 넣고, SQS가 Lambda를 호출해 Fargate
작업 하나를 시작한다. 작업은 지원자 한 명만 분석하고 종료한다. 실행 중인 작업이 있으면
다음 메시지는 큐에서 기다리므로 Gemini 요청이 겹치지 않는다.

`EventBridge Scheduler`는 메시지 발행 누락이나 작업 시작 실패를 복구하기 위해 1시간마다
동일한 Lambda를 호출한다. 평상시 분석 시작은 15분 주기가 아니라 SQS 이벤트가 담당한다.

## 최초 설정

Secrets Manager 런타임 시크릿과 아래 GitHub 값을 등록한다.

- Secret `ANALYSIS_RUNTIME_SECRET_ARN`
- Variable `ANALYSIS_SUBNET_IDS`
- Variable `ANALYSIS_SECURITY_GROUP_IDS`
- Variable `ANALYSIS_ASSIGN_PUBLIC_IP`
- Variable `EC2_INSTANCE_ID`

GitHub 배포 역할에는 기존 권한과 함께 아래 작업이 필요하다.

```json
[
  "sqs:CreateQueue",
  "sqs:DeleteQueue",
  "sqs:GetQueueAttributes",
  "sqs:GetQueueUrl",
  "sqs:SetQueueAttributes",
  "sqs:TagQueue",
  "sqs:UntagQueue",
  "sqs:ListQueueTags",
  "sqs:ListQueues",
  "lambda:CreateEventSourceMapping",
  "lambda:GetEventSourceMapping",
  "lambda:UpdateEventSourceMapping",
  "lambda:DeleteEventSourceMapping",
  "lambda:ListEventSourceMappings"
]
```

큐 정책이 `hiselectors-ec2-role`에 `sqs:SendMessage`를 허용하므로 EC2 역할에 별도 인라인
정책은 필요 없다. 역할 이름이 다르면 CloudFormation의 `ApiInstanceRoleName` 값을 바꾼다.

## 배포 순서

1. `Deploy analysis worker`를 실행해 SQS/Lambda/Fargate 스택을 배포한다.
2. 변경 코드가 `dev`에 반영되면 `Deploy production`이 API 이미지를 배포한다.
3. Production 워크플로가 스택의 `AnalysisQueueUrl`을 `/srv/hiselectors/.env`의
   `APPLICATION_CONTENT_ANALYSIS_QUEUE_URL`에 자동으로 넣는다.
4. EC2 API에는 `APPLICATION_CONTENT_ANALYSIS_SCHEDULER_ENABLED=false`를 유지한다.
5. 검증 후 `Deploy analysis worker`를 `schedule_state=ENABLED`로 실행한다.

## 확인

미디어 수집을 한 건 완료한 뒤 큐와 작업 로그를 확인한다.

```bash
aws sqs get-queue-attributes \
  --region ap-northeast-2 \
  --queue-url "$(aws cloudformation describe-stacks \
    --region ap-northeast-2 \
    --stack-name hiselectors-analysis \
    --query "Stacks[0].Outputs[?OutputKey=='AnalysisQueueUrl'].OutputValue | [0]" \
    --output text)" \
  --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible

aws logs tail /hiselectors/analysis-job \
  --region ap-northeast-2 \
  --since 10m \
  --follow
```

수동 복구 실행은 기존처럼 Lambda를 직접 호출한다.

```bash
aws lambda invoke \
  --region ap-northeast-2 \
  --function-name hiselectors-analysis-dispatcher \
  --cli-binary-format raw-in-base64-out \
  --payload '{}' \
  /tmp/analysis-dispatch.json
```
