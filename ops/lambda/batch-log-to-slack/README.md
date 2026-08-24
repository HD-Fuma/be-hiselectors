# Batch log to Slack Lambda

CloudWatch Logs의 `BATCH_EVENT ` 레코드를 검증해 기존 SNS 주제와 Amazon Q를 거쳐 Slack으로 전달한다. 런타임은 Python 3.14이고, 배포 패키지에는 `lambda_function.py`만 들어간다. Lambda 기본 제공 `boto3` 외 의존성은 없다.

## 전송 상태 매트릭스

| batch | status | Slack 전송 |
|---|---|---|
| `content-sync` | `STARTED` | 안 함 |
| `content-sync` | `SUCCEEDED`, `PARTIAL_FAILURE`, `FAILED`, `SKIPPED` | 함 |
| `task-run` | `SUCCEEDED` | 안 함 |
| `task-run` | `PARTIAL_FAILED`, `FAILED`, `STALE` | 함 |
| 그 외 기존 batch | `STARTED`, `SKIPPED` | 안 함 |
| 그 외 기존 batch | `SUCCEEDED`, `PARTIAL_FAILURE`, `FAILED` | 함 |

`PARTIAL_FAILED`와 `STALE`는 `task-run`에서만 유효하다. `task-run` 이벤트는 nonblank 문자열인 `details.taskType`과 `details.triggerType`을 반드시 포함해야 한다. 기존 `content-sync`의 `PARTIAL_FAILURE`와 `SKIPPED` 동작은 유지한다.

`content-sync`는 플랫폼별 수치를 한 메시지로 묶는다.

````text
⚠️ 콘텐츠 동기화 부분 실패

```
플랫폼 | 신규 후보 | 셀렉터스 | 수정 감지 | 버전 저장 | 실패
Instagram | 14 | 6 | 2 | 8 | 0
YouTube | 7 | 3 | 1 | 4 | 3
합계 | 21 | 9 | 3 | 12 | 3
```
실행 시간: 12.4초
실행 ID: 9fb63104-feca-4c92-a04c-4ebdc4a0bf6f
````

## 배포 전 필수 확인

아래 두 값만 실제 환경에 맞게 바꾼다.

```bash
EXECUTION_ROLE_NAME="<EXECUTION_ROLE_NAME>"
VERIFIED_APP_LOG_GROUP_NAME="<VERIFIED_APP_LOG_GROUP_NAME>"
```

`VERIFIED_APP_LOG_GROUP_NAME`은 Spring 애플리케이션 stdout이 실제로 들어오는 로그 그룹이어야 한다. 먼저 확인한다.

```bash
aws logs describe-log-streams \
  --region ap-northeast-2 \
  --log-group-name "${VERIFIED_APP_LOG_GROUP_NAME}" \
  --order-by LastEventTime \
  --descending \
  --max-items 5

aws logs tail "${VERIFIED_APP_LOG_GROUP_NAME}" \
  --region ap-northeast-2 \
  --since 10m \
  --follow
```

애플리케이션에서 무해한 로그 한 줄을 발생시켜 위 tail에서 확인한다. 확인되지 않으면 여기서 배포를 중단한다. RDS audit 또는 SageMaker 로그 그룹을 추측해서 사용하지 않는다.

## 테스트와 패키징

저장소 루트에서 실행한다.

```bash
python3 -m unittest discover \
  -s ops/lambda/batch-log-to-slack \
  -p 'test_*.py' \
  -v

cd ops/lambda/batch-log-to-slack
zip -j /tmp/hiselectors-batch-log-to-slack.zip lambda_function.py
cd ../../..
```

## IAM 설정

실행 역할에는 일반 Lambda 로그 권한과 이 디렉터리의 SNS 최소 권한 정책을 붙인다.

```bash
aws iam attach-role-policy \
  --role-name "${EXECUTION_ROLE_NAME}" \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam put-role-policy \
  --role-name "${EXECUTION_ROLE_NAME}" \
  --policy-name HiselectorsBatchLogToSlackSnsPublish \
  --policy-document file://ops/lambda/batch-log-to-slack/iam-policy.json

EXECUTION_ROLE_ARN="$(aws iam get-role \
  --role-name "${EXECUTION_ROLE_NAME}" \
  --query 'Role.Arn' \
  --output text)"
```

## 최초 함수 생성

```bash
aws lambda create-function \
  --region ap-northeast-2 \
  --function-name hiselectors-batch-log-to-slack \
  --runtime python3.14 \
  --handler lambda_function.lambda_handler \
  --role "${EXECUTION_ROLE_ARN}" \
  --zip-file fileb:///tmp/hiselectors-batch-log-to-slack.zip \
  --timeout 10 \
  --memory-size 128 \
  --environment 'Variables={SNS_TOPIC_ARN=arn:aws:sns:ap-northeast-2:167595589232:batch-alerts}'
```

## 기존 함수 업데이트

```bash
aws lambda update-function-code \
  --region ap-northeast-2 \
  --function-name hiselectors-batch-log-to-slack \
  --zip-file fileb:///tmp/hiselectors-batch-log-to-slack.zip

aws lambda wait function-updated \
  --region ap-northeast-2 \
  --function-name hiselectors-batch-log-to-slack

aws lambda update-function-configuration \
  --region ap-northeast-2 \
  --function-name hiselectors-batch-log-to-slack \
  --runtime python3.14 \
  --handler lambda_function.lambda_handler \
  --timeout 10 \
  --memory-size 128 \
  --environment 'Variables={SNS_TOPIC_ARN=arn:aws:sns:ap-northeast-2:167595589232:batch-alerts}'
```

## CloudWatch Logs 연결

로그 그룹 확인을 마친 뒤에만 실행한다.

```bash
LOG_GROUP_ARN="arn:aws:logs:ap-northeast-2:167595589232:log-group:${VERIFIED_APP_LOG_GROUP_NAME}:*"

aws lambda add-permission \
  --region ap-northeast-2 \
  --function-name hiselectors-batch-log-to-slack \
  --statement-id AllowVerifiedAppCloudWatchLogs \
  --action lambda:InvokeFunction \
  --principal logs.ap-northeast-2.amazonaws.com \
  --source-account 167595589232 \
  --source-arn "${LOG_GROUP_ARN}"

aws logs put-subscription-filter \
  --region ap-northeast-2 \
  --log-group-name "${VERIFIED_APP_LOG_GROUP_NAME}" \
  --filter-name batch-events-to-slack \
  --filter-pattern '"BATCH_EVENT"' \
  --destination-arn arn:aws:lambda:ap-northeast-2:167595589232:function:hiselectors-batch-log-to-slack
```

같은 statement ID가 이미 있으면 `add-permission`은 생략한다. 연결 상태는 다음 두 명령으로 확인한다.

```bash
aws lambda get-policy \
  --region ap-northeast-2 \
  --function-name hiselectors-batch-log-to-slack

aws logs describe-subscription-filters \
  --region ap-northeast-2 \
  --log-group-name "${VERIFIED_APP_LOG_GROUP_NAME}"
```

## 콘솔 테스트 이벤트

Lambda 콘솔의 Test에 아래 JSON을 붙여 넣는다. 실행 시 Slack에 `content-sync` 성공 메시지가 한 건 와야 한다.

```json
{
  "awslogs": {
    "data": "H4sIAAAAAAAC/z2Qy26DMBBFf6Xytlgx5pHAjiYo7SJZFJJNqSJjhgQpPGSbVFHEv3dwH15Y9r1nxnf8IC1oLc6Q3wcgMdkkeXLapVmWbFPikP6rA4WyGy6DKAhWEfc4ytf+vFX9OKCzuIFq6gaqhRiGayOFafpugQA9W8LCmVEgWqRl3+n+CtSANj9WeoPOaBJ/PEhTzS+hbBoMZUSL/d3lauktvZDNy/kLi9xLkq9fT+kx3edPj4JoeYFWHEFpfL7AOqcgMLfGc/ELvx/2BUG9FEZerI5xDDJU3ztpLTV2b5W1oroMPZf5tAYpqC8jTgXzJfWhrKQvWFmHtS3BoGbUtiY7rNdpukk31vifwnqc8ZCyFeU8d73YZzFjzyzC3bLVqOzH7eZGLg+cOduIH4NXnG5QvcTJYY7mThOZPqdvp3eUNLkBAAA="
  }
}
```

## 롤백

CloudWatch 구독을 먼저 제거해야 재시도와 전달 오류를 막을 수 있다. 그 다음 Lambda와 인라인 정책을 제거하고, 필요할 때만 애플리케이션 변경을 롤백한다.

```bash
aws logs delete-subscription-filter \
  --region ap-northeast-2 \
  --log-group-name "${VERIFIED_APP_LOG_GROUP_NAME}" \
  --filter-name batch-events-to-slack

aws lambda delete-function \
  --region ap-northeast-2 \
  --function-name hiselectors-batch-log-to-slack

aws iam delete-role-policy \
  --role-name "${EXECUTION_ROLE_NAME}" \
  --policy-name HiselectorsBatchLogToSlackSnsPublish
```
