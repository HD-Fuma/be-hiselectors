# TaskRun 큐 worker 배포·중단·복구

이 문서는 코드와 인프라 준비 절차이며, GitHub 병합만으로 큐 발행을 활성화하지 않는다.
인프라 parameter와 준비 명령은 [README](README.md)를 따른다.

## 배포 계약

- 기존 API blue/green, test-listener readiness 승인, 3분 bake, 실패 rollback을 유지한다.
- GitHub variables `ECS_BATCH_WORKER_SERVICE`, `ECS_BATCH_WORKER_CONTAINER_NAME`은 선택 사항이다.
  둘 다 없으면 worker 배포를 건너뛴다. 하나만 있거나 API/scheduler와 같은 service이면 실패한다.
- 순서는 `021 스키마 확인 → worker 이미지 → API blue/green → scheduler 이미지`다.
  같은 SHA 이미지로 맞추며, workflow가 desired count나 `TASK_QUEUE_*` 값을 바꾸지 않는다.
- `021_task_run_queue.sql`은 항상 실행한다. 최초 schema 적용 실패 뒤 다음 코드 push에서도
  재시도하기 위해서다. 한 개의 기존 migration task를 재사용하고 `016`은 해당 파일 변경 또는
  수동 workflow 실행일 때만 포함한다. 기존 금전 데이터와 `016` SQL 파일은 수정하지 않는다.
- `021`은 없는 6개 컬럼과 2개 index만 추가한다. 기존 작업은 `queue_managed=false`로 남는다.
  같은 이름의 컬럼/index가 있어도 타입·null/default·index 순서가 다르면 검증 실패로 배포를 막는다.
  자동 DROP/MODIFY로 맞추지 않는다. metadata lock을 15초 이상 기다리면 실패하므로 별도 점검 후 재실행한다.
- worker `services-stable`만으로 성공 처리하지 않는다. 새 task definition 유지, desired count 불변,
  실제 RUNNING task의 새 revision 및 `HEALTHY`를 확인한다. 자동 rollback은 실패로 처리한다.
  `desired=0`은 이미지 준비 완료일 뿐 `ready=false`다.
- API 또는 scheduler의 현재 `TASK_QUEUE_ENABLED=true`이면 이번 배포의 worker가 ready여야 한다.
  worker variables를 빼거나 desired를 0으로 만들고 publisher를 계속 켜두는 구성은 허용하지 않는다.

## 최초 전환: 준비와 활성화를 분리

1. 현재 운영 DB/image/desired count를 확인하고, 인프라는 publisher 비활성 상태로 준비한다.
   CloudFormation의 오래된 `ImageUri`, `SchedulerDesiredCount`가 운영값을 덮어쓰지 않도록 한다.
2. 기존 프로세스 내부 TaskRun의 신규 제출 경로를 잠시 중지하고, `queue_managed=false`인
   `QUEUED`/`RUNNING` 작업을 확인·완료한다. 새 코드가 이 작업을 SQS로 자동 이전하거나 복구하지 않는다.
   진행 중 작업이 남은 상태에서 old API/scheduler를 종료해도 안전하다고 가정하지 않는다.
3. worker desired 0과 publisher false로 새 release를 배포한다. schema 021 검증과 API/scheduler의
   기존 경로 동작을 확인한다. 이 단계는 작업 실행 방식을 바꾸지 않는다.
4. 승인 후 worker만 desired 1로 올린다. 새 SHA, readiness, DB/SQS 권한, DLQ와 로그·알람을 확인한다.
   task가 `HEALTHY`라는 사실만으로 메일/STT 등 모든 업무 의존성을 검증했다고 주장하지 않는다.
5. API/scheduler가 같은 release이고 기존 local 작업이 정리됐음을 확인한 뒤 **별도 승인한
   인프라 변경으로** `TASK_QUEUE_ENABLED`를 켠다. worker는 `TASK_QUEUE_WORKER_ENABLED=true`,
   API/scheduler는 false여야 한다. 우선 부작용이 작은 작업 1건의 DB 상태·수신·완료·ACK를 확인한다.
   금전 처리·메일·메시지 발송을 첫 검증용으로 임의 실행하지 않는다.

`task_run.business_payload`는 업무 입력일 수 있으므로 workflow summary나 일반 로그에 출력하지 않는다.
SQS 메시지에는 run ID만 사용한다. schema 검증은 컬럼 메타데이터만 조회한다.

## worker 배포와 drain

- worker는 ROLLING 100/200이므로 배포 중 두 revision이 동시에 메시지를 받을 수 있다.
  DB claim/lease, 동일 작업의 상태 guard가 이를 안전하게 처리해야 한다. SQS 전달은 exactly-once가 아니다.
- `StopTimeout=120`, Spring shutdown phase 90초는 유예 시간이지 모든 긴 작업의 완료 보장이 아니다.
  무중단 consumer 교체와 실행 중 업무 전체의 원자적 완료는 다른 조건이다.
- 장시간/비멱등 작업이 있다면 먼저 신규 제출을 중지하고 기존 consumer로 drain한다.
  SQS visible/inflight가 0이라는 한 번의 관찰만 믿지 말고 DB의 queue-managed `QUEUED`/`RUNNING`,
  유효 lease, 마지막 enqueue 시각과 재발행 대기까지 함께 확인한다.
  publisher를 끄더라도 durable QUEUED 행이나 지연·inflight 메시지가 자동으로 사라지지 않는다.
- worker가 종료되거나 heartbeat를 잃으면 lease가 만료될 수 있다. 종료 신호를 받았다는 이유만으로
  원래 업무가 실제 중단됐다고 가정하지 않는다. 살아 있는 실행의 lease를 수동 해제하지 않는다.
- drain 중 계속 작업이 생성되면 원하는 종료 시점이 정해지지 않는다. 제출 차단 범위와 재개 순서를
  운영자가 먼저 결정해야 하며, workflow는 이를 위해 scheduler/worker를 임의 중지하지 않는다.

## rollback과 부분 재시도

- API blue/green rollback은 API revision만 되돌린다. 이미 선배포한 worker와 additive schema를
  자동으로 되돌리지 않는다. 따라서 각 release는 직전 publisher/consumer의 payload와 호환돼야 한다.
- 구코드로 돌아갈 때는 신규 제출 차단 → 큐 및 DB 미완료 작업 drain/확인 → publisher 비활성화 →
  worker 중지/검증 → 기존 경로 재개의 순서를 검토한다. 순서 중 한 단계라도 확인하지 못하면 중단한다.
  **021 컬럼/index를 DROP해서 rollback하지 않는다.**
- queue retry와 업무의 부분 재실행은 다르다. 성공한 금전 항목이나 이미 전송한 메일·카카오 메시지를
  작업 전체 재전송으로 반복하지 않는다. SQS ACK 유실·worker 종료·DB commit 이후 오류가 있을 수 있다.
- 자동 업무 재시도는 `CREATOR_SYNC`와 `CONTENT_SYNC`만 허용한다. 콘텐츠 리포트는 큐 실행을
  유지하되 자동 재시도하지 않는다. 중단된 `ContentVersion.INSPECTING`은 현재 재검수 대상에서
  제외되어 TaskRun 재실행만으로 복구되지 않으므로, 아래 도메인 확인 절차를 먼저 따른다.
- 같은 셀렉터의 정산 월/이월은 순서를 유지하고, 지급월 묶음을 쪼개지 않는다. 기존 상태 재검증,
  유일성 제약과 잠금을 유지한다. `forcePaymentPendingRecalculation`을 자동 재시도에 사용하지 않는다.
- 외부 송금/발송은 DB lease만으로 중복 방지되지 않는다. 외부 idempotency key 또는 전송 결과 대사 없이
  불확실한 결과를 자동 재실행하지 않는다. `FAILED`/`STALE`/DLQ 작업은 원인·반영 내역을 먼저 확인한다.
- DLQ purge/redrive, 새 run ID 발급, 기존 상태/lease 수동 변경은 별도 승인·검증 대상이다.
  readiness 실패 시 workflow는 API 업데이트를 막을 뿐 이러한 복구 작업을 자동으로 하지 않는다.

## 관리자 승인 후 실패 작업 재실행

`POST /api/admin/task-runs/{runId}/retry`는 `TASK_QUEUE_ENABLED=true`인 API에서만 제공한다.
관리자 JWT와 원본과 다른 UUID 형식의 `Idempotency-Key`가 필요하다. 요청 시 현재 관리자 계정을
다시 조회하며, 삭제된 계정이나 일반 사용자는 재실행할 수 없다.

- `queue_managed=true`이고 `FAILED`/`PARTIAL_FAILED`/`STALE`로 종료된
  `CREATOR_SYNC`/`CONTENT_SYNC`만 허용한다.
- 이 API는 **원래 범위 전체 재실행이며, 실패 ID만 재실행하는 API가 아니다.** 원래 저장된 입력과
  작업 종류를 보존하되 현재 관리자를 실행자로 기록한다. 예를 들어 카테고리 수집은 원래 category ID
  범위 전체를 새로 실행한다. 성공 항목을 포함한 재처리와 외부 API 비용을 먼저 확인한다.
- 원인과 반영 내역을 확인하고, `STALE`이면 기존 실행이 실제 종료되어 더 이상 업무를 수행하지 않는지
  확인한 뒤 호출한다. 기존 concurrency guard가 잠겨 있으면 이를 수동 해제하지 않고 원인을 조사한다.
- 새 key 최초 요청과 같은 새 key의 재요청은 `202`와 같은 새 실행 ID를 반환한다. 이미 동종 작업이
  실행 중이면 기존 `TASK_ALREADY_RUNNING` 충돌 계약을 유지한다. 원본 key 재사용은 거절한다.
- 콘텐츠 리포트·정산·메일·카카오 발송과 별도 신청 분석 작업은 이 API로 재실행하지 않는다.
  각 도메인의 복구·대사 절차로 넘어가며, 원본 상태를 초기화하거나 성공 항목을 다시 지급·발송하지 않는다.
- 중단된 콘텐츠 리포트는 원래 worker 종료, TaskRun lease, 해당 검수 버전과 저장된 리포트를 먼저
  확인한다. 고아 `INSPECTING` 상태의 확인·해제는 별도 승인된 도메인 복구가 필요하다.
  다른 실행이 검수 중인 버전을 함께 초기화하지 않는다. 이번 변경은 대상별 소유권이나 자동 복구를
  구현하지 않으며, 새 run 생성이나 DLQ redrive만으로 해당 항목까지 복구됐다고 판단하지 않는다.
- 발송 작업의 실행자 계정이 삭제됐거나 login ID가 비어 있으면 영구 명령 오류로 종료하고 DLQ로
  보낸다. 원본 관리자·발송 결과를 확인하기 전 다른 관리자로 대체하여 재발송하지 않는다.
  실제 DB 접속 장애는 이 영구 오류로 분류하지 않고 원본 명령을 보존한다.
- 원본 실행 이력과 DLQ 메시지는 그대로 남는다. terminal run ID를 DLQ에서 redrive하는 것만으로
  업무가 재실행되지 않는다. 이 API도 DLQ purge/redrive를 자동 실행하지 않는다. 확인된 원본 DLQ
  메시지를 정리하는 작업은 새 실행 결과 확인 후 별도 승인 범위로 다룬다.
- 로그에는 원본/새 실행 ID만 남기며 업무 payload는 출력하지 않는다.

## 검증 범위

CI는 `*ContractTest`와 `com.fuma.hiselectors.taskrun.*`를 실행한다. 배포 contract test는 가짜 AWS 응답으로
정상 worker, desired 0, 자동 rollback, unhealthy task의 가드를 확인하고 실제 AWS를 호출하지 않는다.
SQL의 실제 MySQL 적용/재적용과 실제 SQS consumer 동작은 별도 격리 환경 검증 후 운영 전환에서 확인한다.
