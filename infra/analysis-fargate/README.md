# 지원자 분석 Fargate 작업

미디어 수집이 끝나면 API가 FIFO SQS에 지원자 ID를 넣고, SQS가 Lambda를 호출해 Fargate
작업을 시작한다. 현재 메시지는 **분석 시작 신호**다. Lambda는 메시지 body를 작업에
전달하지 않고, worker가 DB에서 대상을 다시 조회해 처리한다. 템플릿은 한 task당 최대
10명을 순차 처리하도록 설정한다. 일반 TaskRun의 완료 확인형 큐와 구분해야 한다.

Lambda가 `RunTask` 요청 성공을 반환하면 신호 메시지가 삭제된다. 이후 실제 분석 실패는
SQS DLQ가 아니라 DB의 상태·재시도 횟수·lease와 다음 worker 실행을 통해 복구한다.
이미 실행 중인 task가 있으면 Lambda가 실패 응답을 반환하므로, 단순 busy도 SQS receive
횟수를 소비할 수 있다. DLQ 알림이 곧 개별 분석의 반복 실패를 의미하지는 않는다.

`EventBridge Scheduler`는 메시지 발행 누락이나 작업 시작 실패를 복구하기 위해 동일한
Lambda를 호출한다. 템플릿 기본값은 **비활성**이며 주기 기본값은 10분이다. 실제 활성화
여부/주기는 배포 parameter를 확인한다. 평상시 분석 시작은 SQS 이벤트가 담당한다.
analysis 컨테이너는 전역 `SCHEDULING_ENABLED=false`이므로 일반 정산·미디어·알림 cron을
함께 등록하지 않고 명시적인 one-shot 분석만 실행한다.

시작 신호가 재시도를 모두 소진해 DLQ로 이동하면 CloudWatch Alarm이 기존
`batch-alerts` SNS 주제(`AlertTopicName`)를 호출한다. 이 주제에 연결된 Amazon Q Slack
채널로 장애 알림이 전달된다.

## 최초 설정

Secrets Manager 런타임 시크릿과 아래 GitHub 값을 등록한다.

런타임 시크릿에는 `GEMINI_API_KEY`와 예비 키를 쉼표로 구분한 `GEMINI_API_KEYS`를 모두
넣는다. Fargate는 기본 모델이 실패하면 `GeminiFallbackModels`의 모델들을 같은 키로 먼저
시도하고, 이후 다음 키로 넘어간다.

- Secret `ANALYSIS_RUNTIME_SECRET_ARN`
- Variable `ANALYSIS_SUBNET_IDS`
- Variable `ANALYSIS_SECURITY_GROUP_IDS` (production stack의 `TaskSecurityGroupId` 하나)
- Variable `ANALYSIS_ASSIGN_PUBLIC_IP`

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
  "lambda:ListEventSourceMappings",
  "cloudwatch:PutMetricAlarm",
  "cloudwatch:DeleteAlarms",
  "cloudwatch:DescribeAlarms",
  "cloudwatch:TagResource",
  "cloudwatch:UntagResource",
  "cloudwatch:ListTagsForResource"
]
```

CloudWatch 권한의 리소스는
`arn:aws:cloudwatch:ap-northeast-2:167595589232:alarm:hiselectors-analysis-dlq`로 제한한다.

analysis task는 운영 ECS task security group을 재사용한다. 이 security group은 이미 운영
RDS 3306 접근이 허용돼 있다. 운영 ECS API와 scheduler는 자신의 task role에 있는
`sqs:SendMessage` 권한으로 queue에 발행한다.

## 배포 순서

1. `Deploy analysis worker`를 실행해 SQS/Lambda/Fargate 스택을 배포한다.
2. 운영 ECS runtime secret의 `APPLICATION_CONTENT_ANALYSIS_QUEUE_URL`이 stack output과
   같은지 확인한다.
3. 검증 후 `Deploy analysis worker`를 `schedule_state=ENABLED`로 실행한다.

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
