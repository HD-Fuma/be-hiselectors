# 격리형 ECS Blue/Green 리허설 스택

`template.yaml`은 기존 운영 EC2, RDS, 보안 그룹, DNS, `gha-be-deploy` 역할을 수정하지 않는다. 기존 default VPC의 서로 다른 두 public subnet, 기존 ECR image/repository, 기존 GitHub OIDC provider, 원본 RDS snapshot을 참조하고 나머지는 새로 만든다.

production listener는 HTTPS `443`, HTTP `80`은 HTTPS redirect, green test listener는 HTTP `8080`이다. 템플릿은 ACM 인증서를 참조할 뿐 생성하지 않으며, 실제 DNS와 최종 운영 DB는 수정하지 않는다.

## 사전 조건

- stack 이름은 RDS identifier 제한 때문에 55자 이하로 정한다.
- 두 public subnet은 서로 다른 AZ에 있고 Internet Gateway 기본 경로가 있어야 한다. Fargate task는 public IP를 받아 NAT 없이 outbound 통신한다.
- `TestDbSubnetGroupName`은 같은 VPC의 DB subnet group이어야 한다.
- `TestDbSnapshotIdentifier`는 자동 snapshot ARN 또는 수동 snapshot identifier다. 복원본만 삭제되며 원본 snapshot은 유지된다.
- snapshot을 바꾸려면 같은 stack을 update하지 말고 리허설 stack을 삭제 후 다시 만든다. 고정 DB identifier의 replacement 충돌을 피하기 위함이다.
- ECR repository, image와 GitHub OIDC provider는 이 stack과 같은 AWS account/region에 있어야 한다.
- `RuntimeSecretArn`은 새 리허설 전용 secret이며 기본 AWS Secrets Manager KMS key를 사용한다.
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
       GitHubOidcProviderArn=arn:aws:iam::ACCOUNT:oidc-provider/token.actions.githubusercontent.com \
       AcmCertificateArn=arn:aws:acm:ap-northeast-2:ACCOUNT:certificate/... \
       ProductionListenerIngressCidr=YOUR_ADMIN_PUBLIC_IP/32 \
       TestListenerIngressCidr=YOUR_ADMIN_PUBLIC_IP/32
   ```

3. 복원 snapshot의 DB 이름/계정과 secret의 `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`가 일치하는지 확인한다. `DB_HOST`와 `DB_PORT`는 새 RDS endpoint에서 task definition으로 직접 주입되어 운영 DB 주소를 잘못 넣을 수 없다. 현재 branch와 `2026-08-26` snapshot 조합은 새 복제 DB에 아래 호환 컬럼을 한 번 추가해야 JPA validation을 통과한다.

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

RDS audit log는 `/aws/rds/instance/${STACK_NAME}-test-db/audit`에 기록되고 30일 보관된다. 기존 `hi-selectors-audit` option group을 사용하는 DB에 `EnableCloudwatchLogsExports=audit`를 적용하는 in-place update이며 DB replacement가 아니다.

운영 stack의 실제 scheduler는 `desired/running=1/1`이어도 과거 CloudFormation parameter가 `SchedulerDesiredCount=0`일 수 있다. 운영 stack update에는 아래 값을 명시해 scheduler를 끄는 drift 역적용을 막는다. 다른 parameter는 현재 값을 유지하고 `ImageUri`도 현재 service image로 맞춘다.

```bash
aws cloudformation deploy \
  --stack-name hiselectors-bg-test \
  --template-file infra/prod/template.yaml \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    ApiDesiredCount=2 SchedulerDesiredCount=1 EnableDeploymentPause=true \
    ImageUri=CURRENT_ECS_IMAGE_URI
```

새 `/ecs/hiselectors-bg-test` log group의 `batch-events-to-slack` filter와 Lambda invoke permission이 생성된 뒤 실제 `BATCH_EVENT` 전달을 확인한다. 그 전에는 기존 `/hiselectors/app` filter를 제거하지 않는다. 확인 후 기존 filter와 기존 log group으로 제한된 Lambda permission만 제거하며 Lambda와 SNS topics는 유지한다.

## GitHub workflow 연결

- `GitHubDeployRoleArn` → GitHub variable `ECS_AWS_ROLE_ARN`
- `EcrRepositoryName` → `ECS_ECR_REPOSITORY`
- `ClusterName` → `ECS_CLUSTER`
- `ApiServiceName` → `ECS_SERVICE`
- `ApiContainerName` → `ECS_CONTAINER_NAME`
- `SchedulerServiceName` → `ECS_SCHEDULER_SERVICE`
- `SchedulerContainerName` → `ECS_SCHEDULER_CONTAINER_NAME`

새 role trust는 지정된 `dev` ref 하나로 고정되고, 기존 ECR push, 이 stack의 API와 scheduler service deployment, 새 task/execution role의 `PassRole`만 허용한다.

API 배포는 ECS native `BLUE_GREEN`이다. green이 test listener를 받은 뒤 `POST_TEST_TRAFFIC_SHIFT`에서 최대 30분 pause한다. workflow는 `DescribeServiceDeployments`에서 실제 hook ID를 읽고, 두 target group 모두 desired count만큼 ALB readiness가 `healthy`인지 AWS API로 확인한다. 성공 시 `ContinueServiceDeployment(CONTINUE)`, 실패 시 `ROLLBACK`을 호출하며 미응답이면 자동 rollback한다. GitHub-hosted runner가 ALB에 직접 접속하지 않으므로 listener CIDR을 공개할 필요가 없다.

workflow는 ECS service를 직접 갱신하므로 CloudFormation의 `ImageUri` parameter는 자동으로 바뀌지 않는다. 이후 stack을 update할 때는 현재 service image URI를 `ImageUri`로 함께 넘겨 이전 이미지로 되돌아가지 않게 한다.

HTTPS listener만 적용하는 update에서는 `ImageUri=UsePreviousValue`로 둔다. change set에 `ProductionListener`(Modify, replacement false), `LoadBalancerSecurityGroup`(Modify, replacement false), `HttpRedirectListener`(Add) 외 리소스가 나오면 실행하지 않는다.

API blue-green deployment가 성공하면 workflow는 scheduler task definition도 같은 image SHA로 갱신하고 현재 desired count는 바꾸지 않는다. 리허설 중에는 scheduler가 계속 `0`이며, 나중에 `0`에서 `1`로 전환해도 검증된 API release와 같은 image가 시작된다.

## 스케줄러 전환

컷오버 직전에 `Set production EC2 scheduling` workflow를 `mode=disable`, 확인값 `SET_EC2_SCHEDULING_disable`로 실행한다. 이 workflow는 기존 값을 별도 보관하고 EC2 API를 네 스케줄러 플래그가 `false`인 상태로 재생성할 뿐, ECS scheduler는 건드리지 않는다. 완료를 확인한 뒤 별도 작업으로 ECS scheduler를 `0`에서 `1`로 올린다.

롤백은 순서를 바꾸면 안 된다. 먼저 ECS scheduler를 `1`에서 `0`으로 내리고 `desired/running/pending=0/0/0`을 확인한 다음, workflow를 `mode=restore`, 확인값 `SET_EC2_SCHEDULING_restore`로 실행한다. workflow도 ECS scheduler가 완전히 멈추지 않았으면 복원을 거부하며, 성공하면 컷오버 전에 보관한 EC2 설정값을 그대로 복원한다. 이 확인부터 복원 완료까지는 cutover lock 구간으로 취급해 별도 CloudFormation update나 ECS console/CLI 변경을 금지한다.

stack 삭제 시 `TestDatabase`와 audit log group은 `Retain`되고 DB deletion protection도 유지된다.
삭제가 필요하면 별도 final snapshot을 만든 뒤 명시적으로 처리한다. 원본 snapshot과 기존 운영
리소스는 삭제 대상이 아니다.

STT sidecar는 넣지 않았다. 현재 `Dockerfile`은 Spring API만 만들고 `stt-worker`에는 운영 container image 정의가 없으므로, 필요해질 때 별도 worker image/service로 추가한다.
