# 독립 TaskRun 큐·worker 스택

현재 운영에는 **이 템플릿만 별도 `hiselectors-task-queue` 스택으로 적용**한다.
`infra/prod/template.yaml`의 worker 정의와 동일한 앱/큐 계약을 재사용하되 기존
`hiselectors-bg-test` CloudFormation 스택은 업데이트하지 않는다. 두 템플릿의 큐·worker를
동시에 적용하거나 서로 다른 큐를 API/scheduler에 혼용하지 않는다.

2026-08-31 읽기 전용 점검에서 기존 스택의 저장된 API/scheduler revision·이미지는 실제 서비스보다
오래됐고, 이미 삭제된 `TestDatabase`의 endpoint 출력 참조가 남아 있었다. 기존 스택에 템플릿 전체를
적용하면 원치 않는 재배포나 DB 참조 실패가 발생할 수 있다. 이 파일에는 기존 API/scheduler,
EC2, RDS, ALB, listener, VPC, subnet, security group, log group, DNS 리소스가 없다.

## 생성 범위와 입력

기본 `ResourcePrefix=hiselectors-task-queue`이면 service/family는
`hiselectors-task-queue-worker`, container는 `batch-worker`이다.
새 표준 SQS/DLQ, worker task role/task definition/service, DLQ 알람과 desired 1일 때의 task-count
알람만 만든다. 기존 역할에는 아래 **새 이름의 inline policy만 추가**한다. 기존 정책·OIDC trust를
수정하거나 교체하지 않으므로 먼저 같은 정책 이름이 없는지 확인한다.

| 입력 | 기존 값을 조회해 연결할 대상 |
| --- | --- |
| `ClusterName` | 실제 ECS cluster 이름(ARN 아님) |
| `SubnetIds`, `TaskSecurityGroupId` | 같은 VPC의 public subnets, 운영 DB 접근이 허용된 기존 task SG |
| `TaskExecutionRoleArn`, `TaskExecutionRoleName` | 같은 기존 실행 역할의 ARN과 이름; 기존 ECR/log/runtime-secret 권한 유지 |
| `ApiTaskRoleName` | 현재 API와 scheduler가 공유하는 task role; 새 큐 `SendMessage`만 추가 |
| `GitHubDeployRoleName` | 기존 배포 역할; 새 worker Update/Describe, cluster 제한 ListTasks, 새 task-role PassRole만 추가 |
| `RuntimeSecretArn`, `RuntimeDatabaseHost`, `RuntimeDatabasePort` | 검증된 runtime secret ARN과 DB 주소; DB·비밀 값을 변경하지 않음 |
| `LogGroupName`, `AlertTopicArn` | 기존 BATCH_EVENT 구독이 있는 ECS 로그 그룹과 Slack 연결 SNS ARN |
| `ImageUri` | schema 021과 queue worker를 지원하는 검증된 ARM64 SHA/digest image; 새 worker에만 적용 |
| `BatchWorkerDesiredCount` | 기본 `0`; 준비·검증 후 별도 승인으로 `1` |
| `BatchWorkerMailSecretArn`, `BatchWorkerSttBaseUrl` | 기본 빈 값; 실제 SMTP secret의 두 키와 `/content/reel` 지원 URL을 확인한 경우만 설정 |
| `SttWorkerImageUri` | 기본 빈 값; `/content/reel`을 지원하는 검증된 최신 ARM64 STT image를 넣으면 localhost sidecar와 task memory 2 GiB 활성화 |
| `SttBucket`, `SttEndpoint` | 기존 `hi-selectors-stt`, `whisper-large-v3-async`; 실제 input/output/failure 위치와 일치하는지 확인 |

추가 정책 이름은 `${ResourcePrefix}-publish-task-runs`, `${ResourcePrefix}-deploy-worker`,
메일을 설정한 경우만 `${ResourcePrefix}-read-worker-mail`이다. producer 역할은 새 큐를 수신·삭제할 수
없으며, worker만 해당 큐 receive/delete/visibility/send와 해당 DLQ send를 갖는다. worker는 기존
`hiselectors-analysis.fifo`에 후속 분석 신호를 보내는 권한을 유지한다. 이 큐가 runtime secret의
분석 queue URL과 같은 계정·리전에 있는지 확인한다. 기본 구성에는 S3 추가 권한이 없다.
STT sidecar를 켠 경우에만 **새 worker task role**에 지정 endpoint의 `InvokeEndpointAsync`,
지정 bucket의 `whisper/input/*` PutObject, `whisper/*` GetObject와 prefix 제한 ListBucket을 더한다.
DescribeEndpoint/DeleteObject나 기존 API 역할의 STT 권한은 추가하지 않는다.

GitHub 기존 정책의 ECR push, task definition 등록/조회, 해당 cluster의 DescribeTasks,
기존 실행 역할 PassRole 권한은 재사용한다. 별도 스택이 이 기존 권한을 새로 부여하거나 바꾸지 않는다.
SMTP secret을 추가한다면 **desired 0 상태에서 권한과 secret 설정부터 적용·검증한 뒤** worker를 켠다.
실행 역할 이름과 ARN이 같은 역할인지, runtime secret 읽기와 선택한 SMTP secret/KMS 권한이 충족되는지
확인한다. secret 값은 parameter 파일, change set 설명, 로그에 쓰지 않는다.

STT는 `BatchWorkerSttBaseUrl` 외부 URL과 `SttWorkerImageUri` sidecar 중 하나만 선택한다.
둘 다 넣으면 CloudFormation Rule에서 거절한다. 둘 다 비어 있으면 기존 1 GiB 구성과 앱 기본값을
유지하지만 영상 콘텐츠 검수 준비가 된 것은 아니다. sidecar가 있으면 Java는 HEALTHY 상태를
기다린 뒤 시작하고 `http://127.0.0.1:8900`으로 호출한다. sidecar도 loopback에만 바인딩하며
공개 port mapping 없이 기존 로그 그룹의 `stt` stream prefix를 사용한다.
`STT_BACKEND=sagemaker`를 강제해 로컬 Whisper 실행으로 우회하지 않는다.

이전 EC2의 중지된 STT나 `/content/reel`이 없는 옛 image는 대체재가 아니다. sidecar image의 해당
route, 실제 SageMaker endpoint의 output/failure S3 prefix와 위 IAM 범위를 확인한다.
2 GiB는 초기 구성일 뿐, Java와 OCR/영상 처리가 같은 메모리를 쓰므로 **기동·제한된 샘플을 검증한
뒤에만 publisher를 켠다.** 큰 영상의 메모리·파일 크기·실행 시간 상한을 보장하지 않는다.

## 준비와 전환

1. 아래 오프라인 테스트 후, 현재 계정/리전, 참조 ARN/ID, schema `021`, 이미지와 필수 secret **키 이름**을
   확인한다. 이 템플릿은 `CAPABILITY_IAM`이 필요하다. CloudFormation 권한을 GitHub 역할에 추가하지 않는다.
2. **새 스택 CREATE change set**을 먼저 검토한다. `BatchWorkerDesiredCount=0`을 유지한다.
   초기에는 worker task-count 알람과 optional mail policy를 제외한 8개 새 리소스가 대상이다.
   기존 운영 스택의 Update/Remove/Replace가 보이면 중단한다. 기존 역할 권한의 의도된 추가 두 개는
   새 `AWS::IAM::Policy` 리소스로 나타나며 기존 policy 이름을 재사용하면 안 된다.
3. 승인 후 스택을 생성하고 output의 `BatchWorkerServiceName`, `BatchWorkerContainerName`을 GitHub의
   `ECS_BATCH_WORKER_SERVICE`, `ECS_BATCH_WORKER_CONTAINER_NAME`에 연결한다. API/scheduler의
   `TASK_QUEUE_ENABLED`는 이 스택이 설정하지 않는다. 기본 0은 prepared이지 ready가 아니다.
4. [기존 rollout 계약](../prod/BATCH_WORKER_ROLLOUT.md)에 따라 legacy local TaskRun을 drain하고
   `021 스키마 → worker 이미지 → API → scheduler` 순서를 유지한다. SMTP/STT·DB·SQS 수신 및 visibility
   권한을 확인한 뒤 worker desired를 1로 올린다. readiness는 DB와 최근 queue 활동을 검사하며
   메일/STT의 업무 성공까지 보증하지 않는다.
5. worker의 최신 revision/이미지, running 1/pending 0/HEALTHY와 승인된 격리 테스트를 확인한 후에만
   API/scheduler의 `TASK_QUEUE_ENABLED=true`, `TASK_QUEUE_WORKER_ENABLED=false`, `TASK_QUEUE_URL`,
   `TASK_QUEUE_DLQ_URL`을 **별도 변경으로** 이 스택 output에 맞춘다. 기존 prod 스택을 전체 deploy하여
   전환하지 않는다. 실제 금전 처리·메일·카카오 발송은 임의의 smoke test로 실행하지 않는다.

이 스택도 CI에서 worker 이미지를 교체한 뒤에는 저장된 ImageUri/task-definition 참조와 live revision이
달라질 수 있다. 이후 desired/mail 설정 update 전에 현재 worker image·env·task definition을 확인하고
새 change set이 구 revision으로 되돌리지 않는지 검토한다. 기존 API/scheduler에는 영향이 없어야 한다.
현재 CI는 Java `batch-worker` 이미지만 교체하며 STT sidecar의 고정 이미지는 유지한다.
STT API 계약을 바꾸는 배포에서는 sidecar 이미지도 검증 후 별도 갱신하고, 이때 `ImageUri`에
현재 실행 중인 Java 이미지를 함께 반영해 이전 Java 버전으로 되돌아가지 않도록 한다.

## 운영 경계

- worker는 0.5 vCPU/기본 1 GiB(sidecar 선택 시 2 GiB), 동시 1건, DB pool 3이다.
  sidecar 선택 시 CPU는 그대로이며 Java와 STT/OCR이 공유한다. ALB/port mapping 없이 127.0.0.1에만 바인딩하고
  일반 scheduler·자동 초기화를 끈다. 기존 network/SG는 수정하지 않으며 public IP는 outbound 용도다.
- 큐 보존 4일, DLQ 14일, visibility 300초, lease 120초, heartbeat 30초, 최대 업무 시도 3회다.
  SQS redrive의 receive 5회 제한은 업무 시도 횟수와 다르다. 성공 DB 상태 저장 후 ACK하며
  실패 메시지는 DLQ send 성공 뒤 원본에서 삭제한다. exactly-once라고 주장하지 않는다.
- rolling 100/200, 앱 종료 대기 90초/ECS stop 120초다. 배포 중 두 worker가 잠시 공존할 수 있으므로
  금전·발송·중단된 검수 작업의 자동 전체 재시도를 허용하지 않는다. 세부 재시도 정책은 rollout 문서를 따른다.
- 기존 로그 그룹의 BATCH_EVENT 구독을 재사용한다. DLQ와 worker-count 알람만 추가한다.
  desired 0에는 Fargate 실행 비용이 없지만 큐 요청·알람은 별도이며, desired 1에는 task/public IPv4 비용이 든다.
- rollback은 publisher 신규 접수 중단과 queue/DB 작업 drain·보류 정책을 먼저 결정한다.
  큐/DLQ에는 Retain을 적용했으며 purge/redrive/삭제는 자동으로 하지 않는다. 스택 삭제도 worker를 중단하고
  추가 IAM policy를 제거하므로 운영 중 단순 원복 수단으로 사용하지 않는다. 보존된 큐는 이름 재사용을 막을 수 있다.

```bash
python3 -m unittest discover -s infra/task-queue -p 'test_*.py' -v
```

테스트는 기존 PyYAML과 unittest만 사용하며 AWS나 Docker/Gradle을 실행하지 않는다.
