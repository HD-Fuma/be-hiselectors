# 격리형 ECS Blue/Green 리허설 스택

`template.yaml`은 기존 운영 EC2, RDS, 보안 그룹, DNS, `gha-be-deploy` 역할을 수정하지 않는다. 기존 default VPC의 서로 다른 두 public subnet, 기존 ECR image/repository, 기존 GitHub OIDC provider, 원본 RDS snapshot을 참조하고 나머지는 새로 만든다. API와 scheduler는 `RuntimeDatabase*` 파라미터의 외부 RDS를 사용하고, stack이 만든 `TestDatabase`는 rollback용으로 보존한다.

production listener는 HTTPS `443`, HTTP `80`은 HTTPS redirect, green test listener는 HTTP `8080`이다. 템플릿은 ACM 인증서를 참조할 뿐 생성하지 않으며, 실제 DNS와 최종 운영 DB는 수정하지 않는다.

## 사전 조건

- stack 이름은 RDS identifier 제한 때문에 55자 이하로 정한다.
- 두 public subnet은 서로 다른 AZ에 있고 Internet Gateway 기본 경로가 있어야 한다. Fargate task는 public IP를 받아 NAT 없이 outbound 통신한다.
- `TestDbSubnetGroupName`은 같은 VPC의 DB subnet group이어야 한다.
- `TestDbSnapshotIdentifier`는 자동 snapshot ARN 또는 수동 snapshot identifier다. 복원본만 삭제되며 원본 snapshot은 유지된다.
- snapshot을 바꾸려면 같은 stack을 update하지 말고 리허설 stack을 삭제 후 다시 만든다. 고정 DB identifier의 replacement 충돌을 피하기 위함이다.
- ECR repository, image와 GitHub OIDC provider는 이 stack과 같은 AWS account/region에 있어야 한다.
- `RuntimeSecretArn`은 API·scheduler가 외부 runtime DB에 접속할 계정을 담은 Secrets Manager JSON secret이다.
- `RuntimeDatabaseHost`와 `RuntimeDatabaseIdentifier`는 API·scheduler가 사용하고 알람이 감시할 외부 RDS의 endpoint와 DB instance identifier다.
- `AcmCertificateArn`은 `ap-northeast-2`에서 별도로 발급·DNS 검증한 `api.hiselectors.shop` 인증서 ARN이어야 한다.
- `AlertTopicArn`은 기존 Amazon Q/Slack 구독이 연결된 standard SNS topic ARN이어야 한다.
- `BatchLogLambdaArn`은 `BATCH_EVENT`를 검증해 기존 batch alert topic으로 전달하는 Lambda ARN이어야 한다. 이 stack은 새 ECS log group으로 제한된 invoke permission과 subscription filter만 추가한다.
- 배포 주체는 IAM role 생성 권한과 `CAPABILITY_IAM`이 필요하다. 이 stack이 만드는 GitHub role에는 CloudFormation 권한이 없다.

리허설 secret JSON에는 아래 key가 모두 있어야 한다.

```json
{
  "DB_NAME": "copy-the-snapshot-source-value",
  "DB_USERNAME": "copy-the-snapshot-source-value",
  "DB_PASSWORD": "copy-the-snapshot-source-value",
  "JWT_SECRET": "replace-with-the-rehearsal-jwt-secret",
  "INSTAGRAM_REDIRECT_URI": "http://localhost/unused-in-rehearsal",
  "GEMINI_API_KEY": "copy-the-existing-runtime-value",
  "GEMINI_API_KEYS": "copy-the-existing-runtime-value",
  "META_API_VERSION": "copy-the-existing-runtime-value",
  "META_INSTAGRAM_BUSINESS_ACCOUNT_ID": "copy-the-existing-runtime-value",
  "META_LONG_LIVED_ACCESS_TOKEN": "copy-the-existing-runtime-value"
}
```

DB/JWT 5개는 시작에 필요한 값이고 Gemini/Meta 5개는 기존 분석 기능과 동일한 설정이다. OAuth, Kakao, 메일, S3, STT 기능은 도메인이나 외부 side effect 격리가 필요하므로 아직 연결하지 않는다.

## 생성 순서

1. 정적 검증한다.

   ```bash
   aws cloudformation validate-template \
     --template-body file://infra/prod/template.yaml
   ```

2. `ApiDesiredCount=0`, `SchedulerDesiredCount=0`, `EnableDeploymentPause=false`인 기본값으로 1차 stack을 생성한다. 초기 service deployment가 승인 대기 상태에 걸리지 않게 hook은 아직 넣지 않는다. 필요한 parameter는 `template.yaml` 상단을 따른다.

   ```bash
   aws cloudformation deploy \
     --stack-name hiselectors-bg-test \
     --template-file infra/prod/template.yaml \
     --capabilities CAPABILITY_IAM \
     --parameter-overrides \
       VpcId=vpc-... PublicSubnetAId=subnet-... PublicSubnetBId=subnet-... \
       TestDbSnapshotIdentifier=arn:aws:rds:... \
       TestDbSubnetGroupName=... ImageUri=ACCOUNT.dkr.ecr.REGION.amazonaws.com/be-hiselectors:SHA \
       RuntimeSecretArn=arn:aws:secretsmanager:... \
       RuntimeDatabaseHost=RUNTIME_RDS_ENDPOINT RuntimeDatabasePort=3306 \
       RuntimeDatabaseIdentifier=RUNTIME_RDS_IDENTIFIER \
       GitHubOidcProviderArn=arn:aws:iam::ACCOUNT:oidc-provider/token.actions.githubusercontent.com \
       AcmCertificateArn=arn:aws:acm:ap-northeast-2:ACCOUNT:certificate/... \
       ProductionListenerIngressCidr=YOUR_ADMIN_PUBLIC_IP/32 \
       TestListenerIngressCidr=YOUR_ADMIN_PUBLIC_IP/32
   ```

3. 외부 runtime DB의 이름/계정과 secret의 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`가 일치하는지 확인하고, DB 보안 그룹에 `TaskSecurityGroupId` output의 3306 ingress를 허용한다. `infra/analysis-fargate` stack의 `RuntimeSecretArn` secret에 있는 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`도 같은 runtime DB로 함께 바꿔야 한다. `TestDatabase`는 rollback용이므로 이 전환에서 수정하지 않는다. 현재 branch와 `2026-08-26` snapshot 조합은 rollback DB에 아래 호환 컬럼을 한 번 추가해야 JPA validation을 통과한다.

   ```sql
   ALTER TABLE application_report ADD COLUMN warning VARCHAR(500) NULL;
   ```
4. 같은 stack을 `ApiDesiredCount=2`, `EnableDeploymentPause=false`로 2차 update한다. ALB는 DB를 포함한 `/actuator/health/readiness`, container는 DB 장애와 분리된 `/actuator/health/liveness`를 사용한다.
5. `ProductionUrl`과 `TestUrl`로 검증한 뒤 `EnableDeploymentPause=true`로 마지막 update한다. 이후 image 배포부터 green smoke 승인 hook이 동작한다. 준비 중에는 `ProductionListenerIngressCidr`와 `TestListenerIngressCidr`를 관리자 `/32`로 유지한다.

DNS 전환 직전에 `ProductionListenerIngressCidr=0.0.0.0/0`으로 바꾸고, 가비아의 `api` 레코드만 `LoadBalancerDnsName`으로 교체한다. ACM 검증 CNAME은 인증서 자동 갱신을 위해 유지한다.

`SchedulerDesiredCount`는 리허설 동안 `0`을 유지한다. 나중에 활성화할 때는 기존 EC2를 포함한 다른 모든 scheduler를 먼저 중지한 뒤 `1`로 바꾼다. scheduler rolling 설정은 `MaximumPercent=100`, `MinimumHealthyPercent=0`이라 교체 시 중복 실행 대신 짧은 공백이 생긴다.

API는 측정된 유휴 메모리 사용량을 기준으로 `0.5 vCPU / 1GB`를 사용한다. 스케줄러는 실제 배치 부하를 측정하기 전까지 `1 vCPU / 2GB`를 유지한다.

## 운영 모니터링 전환

stack은 기존 `AlertTopicArn`으로 다음 상태를 알린다.

- API와 scheduler의 `LiveTaskCount`가 각각 desired count 아래로 3분 중 2분 내려간 경우
- API와 scheduler의 평균 memory 사용률이 5분 연속 85% 이상인 경우
- target 또는 ALB가 생성한 5xx가 2분 연속 분당 5건 이상인 경우
- RDS connection, CPU, free memory, free storage가 기존 운영 임계값을 넘긴 경우
- RDS `availability`, `failure`, `low storage` event가 발생한 경우

ECS 기본 `AWS/ECS` metric을 사용하므로 Container Insights는 필요 없다. `ApiDesiredCount=0` 또는 `SchedulerDesiredCount=0`이면 해당 service alarm은 만들지 않는다.

RDS alarm과 event subscription은 `RuntimeDatabaseIdentifier`의 외부 runtime DB를 감시한다. rollback용 `TestDatabase`의 audit log는 `/aws/rds/instance/${STACK_NAME}-test-db/audit`에 기록되고 30일 보관된다.

운영 stack의 실제 scheduler는 `desired/running=1/1`이어도 과거 CloudFormation parameter가 `SchedulerDesiredCount=0`일 수 있다. 일반 운영 stack update에는 아래 값을 명시해 scheduler를 끄는 drift 역적용을 막는다. 다른 parameter는 현재 값을 유지하고 `ImageUri`도 현재 service image로 맞춘다.

```bash
aws cloudformation deploy \
  --stack-name hiselectors-bg-test \
  --template-file infra/prod/template.yaml \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    ApiDesiredCount=2 SchedulerDesiredCount=1 EnableDeploymentPause=true \
    RuntimeDatabaseHost=RUNTIME_RDS_ENDPOINT RuntimeDatabasePort=3306 \
    RuntimeDatabaseIdentifier=RUNTIME_RDS_IDENTIFIER \
    ImageUri=CURRENT_ECS_IMAGE_URI
```

runtime DB cutover는 위 일반 update와 다르게 진행한다. API 쓰기를 잠그고 scheduler와 analysis schedule을 먼저 중지한 뒤 최종 데이터를 동기화한다. 첫 update는 `SchedulerDesiredCount=0 EnableDeploymentPause=false`로 실행해 API green task가 별도 GitHub 승인 없이 새 DB에 붙어 traffic을 전환하게 한다. API readiness를 확인한 뒤 두 번째 update에서 `SchedulerDesiredCount=1 EnableDeploymentPause=true`로 복원하고 analysis secret과 schedule도 같은 DB로 전환한다. 이 순서를 섞으면 두 DB에 쓰기가 갈리는 split-brain이 발생한다.

새 `/ecs/hiselectors-bg-test` log group의 `batch-events-to-slack` filter와 Lambda invoke permission이 생성된 뒤 실제 `BATCH_EVENT` 전달을 확인한다. 그 전에는 기존 `/hiselectors/app` filter를 제거하지 않는다. 확인 후 기존 filter와 기존 log group으로 제한된 Lambda permission만 제거하며 Lambda와 SNS topics는 유지한다.

## GitHub workflow 연결

- `GitHubDeployRoleArn` → GitHub variable `ECS_AWS_ROLE_ARN`
- `EcrRepositoryName` → `ECS_ECR_REPOSITORY`
- `ClusterName` → `ECS_CLUSTER`
- `ApiServiceName` → `ECS_SERVICE`
- `ApiContainerName` → `ECS_CONTAINER_NAME`
- `SchedulerServiceName` → `ECS_SCHEDULER_SERVICE`
- `SchedulerContainerName` → `ECS_SCHEDULER_CONTAINER_NAME`
- `BatchWorkerServiceName` → `ECS_BATCH_WORKER_SERVICE`
- `BatchWorkerContainerName` → `ECS_BATCH_WORKER_CONTAINER_NAME`

새 role trust는 지정된 `dev` ref 하나로 고정되고, 기존 ECR push, 이 stack의 API·scheduler·batch worker service deployment, 해당 task/execution role의 `PassRole`만 허용한다. worker 배포 검증용 `ListTasks`는 이 cluster로 제한한다.

API 배포는 ECS native `BLUE_GREEN`이다. green이 test listener를 받은 뒤 `POST_TEST_TRAFFIC_SHIFT`에서 최대 30분 pause한다. workflow는 `DescribeServiceDeployments`에서 실제 hook ID를 읽고, 두 target group 모두 desired count만큼 ALB readiness가 `healthy`인지 AWS API로 확인한다. 성공 시 `ContinueServiceDeployment(CONTINUE)`, 실패 시 `ROLLBACK`을 호출하며 미응답이면 자동 rollback한다. GitHub-hosted runner가 ALB에 직접 접속하지 않으므로 listener CIDR을 공개할 필요가 없다.

workflow는 ECS service를 직접 갱신하므로 CloudFormation의 `ImageUri` parameter는 자동으로 바뀌지 않는다. 이후 stack을 update할 때는 현재 service image URI를 `ImageUri`로 함께 넘겨 이전 이미지로 되돌아가지 않게 한다.

HTTPS listener만 적용하는 update에서는 `ImageUri=UsePreviousValue`로 둔다. change set에 `ProductionListener`(Modify, replacement false), `LoadBalancerSecurityGroup`(Modify, replacement false), `HttpRedirectListener`(Add) 외 리소스가 나오면 실행하지 않는다.

API blue-green deployment가 성공하면 workflow는 scheduler task definition도 같은 image SHA로 갱신하고 현재 desired count는 바꾸지 않는다. 리허설 중에는 scheduler가 계속 `0`이며, 나중에 `0`에서 `1`로 전환해도 검증된 API release와 같은 image가 시작된다.

## TaskRun 전용 큐와 배치 워커

기존 API·scheduler와 동일한 앱 image를 쓰는 별도 worker를 추가한다. 이 큐는
`infra/analysis-fargate`의 지원자 분석 시작 신호용 FIFO와 **다른 standard SQS**이다.
DB에 기록한 `TaskRun`을 작업 ID로 연결하며, 작업 완료 후 ACK·lease/heartbeat·멱등 처리는
Java worker가 담당한다. SQS 리소스를 생성한 것만으로 모든 배치가 자동 전환되지는 않는다.

| 설정 | 기본값/역할 |
| --- | --- |
| `EnableTaskQueuePublishing` | `false`: API·scheduler의 새 queue env를 아예 생략. Java 기본값도 OFF |
| `BatchWorkerDesiredCount` | `0`: 준비 단계에서는 task를 실행하지 않음. 검증 후 `1` |
| 워커 실행 사양 | ARM64 `0.5 vCPU / 1GB`, 큐 작업 동시 실행 `1`, 기존 executor `2/2`, DB pool 최대 `3` |
| SQS | visibility `300초`, long poll `20초`, 보존 `4일`, redrive receive count `5` |
| DLQ | 보존 `14일`, 메시지 발생 시 기존 `AlertTopicArn`으로 알림 |
| 작업 lease | `120초`, heartbeat `30초`, 업무 시도 상한 `3` (SQS 수신 횟수와 다름) |
| worker 교체 | rolling `100/200`, ECS stop timeout `120초`, 앱 종료 대기 `90초` |

큐는 SQS 관리형 암호화를 사용하고 stack 삭제/replacement 시 `Retain`한다. 보존기간
만료에 따른 메시지 삭제는 그대로 적용되므로 영구 보관은 아니다. 큐와 DLQ를 purge하거나
삭제하는 권한은 앱·worker·일반 배포 역할에 추가하지 않는다. 삭제/이름 변경 후 재생성에는
남아 있는 큐의 명시적 인수 또는 별도 이름이 필요하다.

worker에는 ALB 연결과 port mapping이 없다. 웹 보안 bean 기동 호환을 유지하되
`SERVER_ADDRESS=127.0.0.1`로 컨테이너 내부 health endpoint만 연다. DB ingress를
변경하지 않기 위해 기존 `TaskSecurityGroup`을 재사용한다. 이 SG 자체에는 기존 ALB
ingress가 있으므로 **새 private subnet/SG 격리를 구축했다는 뜻은 아니다**.
public IP는 기존 public subnet에서 SQS/외부 API outbound에 사용하며 NAT는 추가하지 않는다.
일반 `@Scheduled`, discovery/policy 초기화, 분석 run-once는 worker에서 모두 OFF이다.

API·scheduler task role에는 새 큐의 `SendMessage`만 추가한다. worker role에는 해당
큐의 receive/delete/visibility/attributes와 후속 작업 발행, 해당 DLQ 발행, 기존 분석 신호
큐 발행만 허용한다. Java 배치 경로에서 S3 SDK read 필요가 확인되지 않아 S3 권한은
추가하지 않는다. ECR/log/secret 읽기는 기존 ECS execution role을 재사용한다.

자동 업무 재시도와 범용 관리자 재시도 API의 허용 대상은 `CREATOR_SYNC`와 `CONTENT_SYNC`뿐이다.
`CONTENT_REPORT_GENERATION`은 큐에서 실행하지만 자동·범용 수동 재시도는 하지 않는다.
검수 도중 프로세스가 죽으면 `ContentVersion`에 `INSPECTING` 상태가 남을 수 있고, 현재 재검수
대상 조회는 이 상태를 제외한다. TaskRun만 다시 실행하면 미완료 항목을 건너뛸 수 있으므로,
원래 실행 종료와 해당 검수 항목을 확인한 뒤 별도로 승인된 도메인 복구가 필요하다.
이번 변경은 고아 `INSPECTING` 자동 초기화나 항목별 소유권·복구 기능을 추가하지 않는다.
정산·메일·카카오 발송도 자동·범용 관리자 재시도 대상이 아니며 기존 도메인 복구 절차를 유지한다.

### 전환 전 확인: DB뿐 아니라 업무별 외부 연결도 준비

- `021_task_run_queue.sql`이 **현재 runtime DB**에 적용되고 새 앱과 호환되는지 확인한다.
  기존 RDS/EC2, rollback DB를 교체하거나 데이터 이전을 하는 작업이 아니다.
- worker는 필요한 DB/JWT/Gemini/Meta/YouTube/Kakao/정산 secret key만 재사용한다.
  새 SMTP key가 기존 secret에 있다고 가정하지 않는다.
- 제안 메일 작업이 있으면 `BatchWorkerMailSecretArn`에 `MAIL_USERNAME`, `MAIL_PASSWORD`
  두 key가 있는 secret을 별도로 지정한다. 기본값은 빈 문자열이며 이때 SMTP key는
  주입하지 않는다. 현재 앱 기본 SMTP host/port는 `smtp.gmail.com:587`이다. 실제 발송 전
  테스트 수신자와 송신 계정을 확인해야 한다. secret에 별도 KMS CMK를 사용하면 해당
  execution role의 그 key에 한정된 decrypt 권한도 별도 검토한다.
- 인스타그램 콘텐츠 검수는 `BatchWorkerSttBaseUrl`에 `/content/reel`을 지원하는 접근 가능한
  STT/OCR 서비스 주소가 필요하다. 기본값 빈 문자열은 **연결 준비 완료를 의미하지 않는다**.
  STT sidecar를 이 worker에 추가하지 않았으며, 확인되지 않은 기존 주소를 임의 복사하지 않는다.
- worker readiness는 DB와 최근 SQS 수신 또는 heartbeat/visibility 갱신 성공까지 확인한다.
  최초 성공한 SQS 수신 전이나 성공 기록이 90초 이상 오래되면 `taskQueue`가 DOWN이다.
  health 응답에는 queue URL/인증 정보가 없고 probe가 추가 SQS 요청을 만들지 않는다.
  이 상태가 SMTP·Gemini·STT·Kakao 업무 성공까지 보장하지는 않으므로 전환 대상 작업별로
  격리된 테스트 데이터와 허용된 외부 대상에서 확인한다.

### 단계별 rollout: worker 준비 후 publisher는 마지막에

배포/원복의 실행 계약과 legacy 작업 drain은 [상세 rollout 문서](BATCH_WORKER_ROLLOUT.md)를 함께 따른다.

1. 오프라인 검증: 기존 PyYAML이 있는 Python 환경에서
   `python3 -m unittest discover -s infra/prod -p 'test_*.py' -v`를 실행한다.
   이는 YAML/권한/역할별 flag/초기값 contract 검증이며 AWS change set 검증을 대신하지 않는다.
2. 실제 API·scheduler의 image, task definition env, desired count, DB host, listener와 현재
   CloudFormation parameter drift를 읽기 전용으로 비교한다. 기존 image/환경/desired가 서로
   불일치하면 먼저 변경 의도를 확정한다. **이 템플릿 전체를 곧바로 deploy하지 않는다.**
3. `EnableTaskQueuePublishing=false`, `BatchWorkerDesiredCount=0`으로 큐/worker 준비용 change
   set을 검토한다. 새 리소스와 정확히 필요한 IAM 추가 이외에 기존 API/scheduler 재배포,
   RDS·네트워크·listener 변경이나 replacement가 섞이면 중단한다. OFF의 `AWS::NoValue`는
   새 publisher env 변경만 줄여 줄 뿐 기존 CloudFormation drift 전체를 해결하지 않는다.
4. **새 API image를 배포하기 전에** legacy TaskRun의 신규 접수를 제한하고 이미 실행 중인
   `queue_managed=false` 작업을 drain한다. 기존 배포 절차로 schema `021`과 새 image를 준비하고
   workflow의 optional worker 변수를 연결한다. desired `0`에서 image가 정렬된 상태는
   `prepared`일 뿐 처리 가능한 `ready`가 아니다.
5. 필요한 SMTP/STT 설정과 queue IAM/DB 연결을 검증하고 `BatchWorkerDesiredCount=1`을
   명시적으로 승인된 change set에서 적용한다. 최신 task definition, `running=1/pending=0`,
   container readiness와 worker 소비·heartbeat·실패 격리를 테스트한다. 테스트 실행·메일·정산은
   각각 별도 허용된 대상과 범위에서만 수행한다.
6. 기존 JVM의 legacy TaskRun이 여전히 정리된 상태인지 재확인한 뒤 `EnableTaskQueuePublishing=true`로
   API·scheduler를 전환한다. worker가 없거나 `desired=0`이면 publisher 전환을 진행하지 않는다.
   반복 전달, worker 재시작, 실패 job의 DLQ 기록, 업무 중복 방지를 검증한 뒤 완료로 기록한다.

원복할 때는 큐를 지우지 않는다. 새 접수를 멈추고 기존 큐/진행 중 작업의 drain 또는 보류
정책을 결정한 다음 publisher flag를 되돌린다. 이미 `QUEUED`/`RUNNING`인 큐 작업을 옛 JVM
executor가 이어받는다고 가정하지 않는다. DB schema `021`도 유지하며 DLQ는 원인 확인 후
작업 상태/멱등 키와 함께 제한적으로 재처리한다. 현재 worker가 DB terminal 상태를 어떻게
처리하는지 확인하지 않고 SQS 콘솔의 대량 redrive만 실행해서는 안 된다.

비용은 worker desired `0`이면 새 Fargate 실행 비용이 없고, `1`이면 소형 task와 public IPv4,
SQS 요청·추가 alarm 비용이 발생한다. rolling 교체 동안 구·신 worker가 잠시 함께 실행된다.
기존 API 수량·RDS 사양·ALB·EC2 설정은 이 기능을 위해 낮추거나 삭제하지 않는다.

## 스케줄러 전환

컷오버 직전에 `Set production EC2 scheduling` workflow를 `mode=disable`, 확인값 `SET_EC2_SCHEDULING_disable`로 실행한다. 이 workflow는 기존 값을 별도 보관하고 EC2 API를 네 스케줄러 플래그가 `false`인 상태로 재생성할 뿐, ECS scheduler는 건드리지 않는다. 완료를 확인한 뒤 별도 작업으로 ECS scheduler를 `0`에서 `1`로 올린다.

롤백은 순서를 바꾸면 안 된다. 먼저 ECS scheduler를 `1`에서 `0`으로 내리고 `desired/running/pending=0/0/0`을 확인한 다음, workflow를 `mode=restore`, 확인값 `SET_EC2_SCHEDULING_restore`로 실행한다. workflow도 ECS scheduler가 완전히 멈추지 않았으면 복원을 거부하며, 성공하면 컷오버 전에 보관한 EC2 설정값을 그대로 복원한다. 이 확인부터 복원 완료까지는 cutover lock 구간으로 취급해 별도 CloudFormation update나 ECS console/CLI 변경을 금지한다.

stack 삭제 시 rollback용 `TestDatabase`와 audit log group은 `Retain`되고 DB deletion protection도 유지된다.
삭제가 필요하면 별도 final snapshot을 만든 뒤 명시적으로 처리한다. 원본 snapshot과 기존 운영
리소스는 삭제 대상이 아니다.

STT sidecar는 넣지 않았다. 현재 `Dockerfile`은 Spring API만 만들고 `stt-worker`에는 운영 container image 정의가 없으므로, 필요해질 때 별도 worker image/service로 추가한다.
