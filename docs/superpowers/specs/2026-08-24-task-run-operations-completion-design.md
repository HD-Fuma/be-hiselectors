# TaskRun 운영 보완 설계

## 목표

TaskRun 실패를 기존 구조화 로그·Slack 경로에 연결하고, 정산 재계산의 세부 결과를 보존하며,
관리자가 완료 이력을 별도 화면에서 확인할 수 있게 한다. 로컬 관리자 이름 깨짐은 운영 코드가
아닌 손상된 로컬 데이터만 수정한다.

## 범위

- `PARTIAL_FAILED`, `FAILED`, `STALE` TaskRun을 `BATCH_EVENT` 로그로 기록한다.
- 기존 CloudWatch Logs → Lambda → SNS/Amazon Q → Slack 경로를 재사용한다.
- 정산 재계산 완료 문구에 신규·수정·확정·실패·건너뜀 건수를 저장한다.
- 관리자 화면에 `/task-runs` 완료 이력 페이지를 추가한다.
- 플로팅 패널 폴링 대기시간을 2초에서 1초로 줄인다.
- 로컬 복제 DB의 `admin1` 이름 데이터만 정상 UTF-8 값으로 수정한다.

## TaskRun 실패 로그와 Slack

### 이벤트 대상

- `PARTIAL_FAILED`
- `FAILED`
- `STALE`

정상 완료는 Slack 대상에서 제외한다. 실행 예외, 진행 건수 기반 실패, executor 거절, stale 판정을
모두 포함한다.

### 기록 시점

알림은 terminal 상태 전환 트랜잭션이 커밋된 다음 기록한다. 구조화 로그 또는 Slack 경로가
실패해도 이미 저장된 TaskRun 상태는 롤백하지 않는다. 호출자는 logger 예외를 잡아 운영 로그만
남긴다. 실제 상태 전이가 없거나 리스를 잃은 실행자는 terminal snapshot을 받지 않으므로 알림을
기록하지 않는다.

- 일반 완료·예외: `complete` 또는 `fail`이 반환되고 트랜잭션이 커밋된 다음
  `TaskRunExecutionService`가 실패 상태만 기록한다.
- executor 거절: `failQueued`가 반환되고 커밋된 다음 기록한다.
- stale: `TransactionTemplate`이 실제 전이 여부와 immutable terminal snapshot을 반환하고,
  transaction 종료 후 scheduler가 기록한다.

### 이벤트 계약

`BatchEventLogger.taskRunTerminal(...)`은 기존 Lambda가 읽는 envelope를 유지한다.

- `schemaVersion=1`
- `event=BATCH_RUN`
- `batch=task-run`
- 실제 TaskRun `runId`
- TaskRun terminal `status`
- `timestamp=finishedAt`
- `durationMs`: `startedAt`이 있으면 `finishedAt - startedAt`, 없으면 `0`
- `counts`: `processed`, `succeeded`, `failed`, `skipped`는 항상 포함하고, `total`은
  `totalCount != null`일 때만 포함한다.
- `details`: `taskType`, `triggerType`
- `error`: 모든 알림 대상 상태에서 `{type, message}`를 필수로 보낸다. 저장된 오류 유형과 메시지는
  각각 독립적으로 nonblank인지 확인하고 메시지는 500자로 제한한다. 값이 없으면 상태별 기본값을
  사용한다.
  - `FAILED`: `TASK_RUN_FAILED` / `처리 결과에 실패 건수가 포함되어 있습니다.`
  - `PARTIAL_FAILED`: `TASK_RUN_PARTIAL_FAILED` / `일부 처리 항목이 실패했습니다.`
  - `STALE`: `TASK_RUN_STALE` / `제한 시간 동안 heartbeat가 없어 비정상 종료로 판정했습니다.`

기존 `BatchEventLogger`에 TaskRun terminal 이벤트를 기록하는 작은 API를 추가한다. 기존
`STARTED` 컨텍스트의 임의 UUID를 사용하지 않는다. `runId`를 Slack 메시지에 표시해 로그 전달
계층의 재시도로 중복이 생겨도 같은 실행임을 식별할 수 있게 한다.

Lambda는 기존 `PARTIAL_FAILURE`를 유지하면서 TaskRun의 `PARTIAL_FAILED`, `STALE`를
`ALLOWED_STATUSES`, publish 대상, 제목 매핑에 모두 추가한다. Slack 제목과 본문에는
`taskType`과 실제 `runId`를 표시한다. 새 outbox 테이블은 추가하지 않는다. 보장은 exactly-once가
아니라 **한 프로세스에서 성공한 상태 전이당 한 번 기록 시도**다.

## 정산 세부 결과

`SettlementRecalculationTask`는 기존 결과의 다음 값을 최종 `progress_message`로 기록한다.

```text
신규 3건 · 수정 5건 · 확정 2건 · 실패 1건 · 건너뜀 4건
```

`progress_message`는 이미 lease 검증과 진행률 flush를 통해 TaskRun에 저장되고 API에 포함된다.
따라서 새 JSON 컬럼이나 정산 결과 테이블은 만들지 않는다. 공용 성공 건수는 기존처럼
`created + updated + finalized`로 유지한다.

결과를 받은 뒤 `describe("신규 …")`를 호출하고 이어서
`advance(created + updated + finalized, failed, skipped)`를 호출한다. threshold flush가 발생하면
문구와 count가 같은 flush에 저장되고, threshold에 도달하지 않은 값은 ExecutionService의 최종
`flush()`가 함께 저장한다.

## 완료 이력 화면

사이드바에 `작업 > 실행 이력` 메뉴를 추가하고 `/task-runs`로 연결한다. 기존
`GET /api/admin/task-runs/recent?page={page}&size=20`을 사용하며 별도 백엔드 API는 만들지 않는다.

표시 항목은 다음 여섯 개다.

- 종료 시각
- 시작 시각
- 작업
- 상태
- 실행 주체
- 처리 결과

페이지당 20건을 조회한다. 필터, 상세 drawer, 별도 실시간 폴링은 만들지 않는다. 삭제된 관리자나
이름이 없는 경우 `관리자 실행`, 시작 시각이 없는 STALE 실행은 `-`로 표시한다.

프론트 모델은 백엔드 계약에 맞춰 `startedAt: string | null`, `finishedAt: string | null`을 갖고
`startedBy.name`도 nullable로 처리한다. API 응답은 `ApiResult<SpringPage<TaskRun>>`이며
`content`, `number`, `totalPages`, `totalElements`를 검증한다. UI의 1-based 페이지 `n`은 Spring의
0-based 요청 `page=n-1&size=20`으로 변환한다.

`처리 결과`는 모든 task type에서 nonblank `progressMessage`를 최우선 표시하고, 없을 때만 기존
count/status 요약을 사용한다. 종료 시각은 `finishedAt`, 시작 시각은 nullable `startedAt`,
scheduled 실행은 `자동 실행`, 삭제됐거나 빈 관리자 이름은 `관리자 실행`으로 표시한다.

## 플로팅 패널 폴링

요청이 끝난 뒤 다음 요청을 예약하는 기존 직렬 폴링 구조를 유지하고 대기시간만 2,000ms에서
1,000ms로 변경한다. 요청 중복, setInterval, 별도 설정 객체는 추가하지 않는다.

## 관리자 이름 깨짐

현재 로컬 복제 DB의 `admin1.name` 바이트가 이중 UTF-8로 저장돼 있다. JDBC와 JSON 응답은
정상이며 운영 코드에서 문자열을 재변환하면 정상 데이터가 손상된다. 따라서
`hiselectors_taskrun_local.admin`에서 `login_id = 'admin1'`인 행이 정확히 1개인지 먼저 확인하고,
그 행만 `name = '로컬 관리자'`로 수정한다. 수정 후 `name`과 `HEX(name)`을 검증한다. 이 SQL은
로컬 복제 DB에서만 실행하고 저장소 migration, seed, 운영 배포 파일에는 추가하지 않는다.

## 테스트

- TaskRun 실행 완료·예외·executor 거절·stale별 실패 이벤트가 한 프로세스의 성공한 상태 전이당
  한 번 시도되는지 검증한다.
- 성공과 리스 상실은 실패 이벤트를 기록하지 않는지 검증한다.
- 로거 실패가 terminal 상태를 되돌리지 않는지 검증한다.
- total이 없는 TaskRun은 `counts.total`을 생략하고, 상태별 nonblank 기본 error를 사용하는지
  검증한다.
- Lambda가 기존 상태와 새 `PARTIAL_FAILED`, `STALE`를 처리하는지 검증한다.
- 정산 세부 문구와 기존 진행 건수 집계가 같은 flush에 저장되는지 검증한다.
- 완료 이력의 성공·실패·빈 결과·페이지 이동과 progressMessage 우선 표시를 검증한다.
- 폴링이 요청 완료 후 1초에 재실행되고 비활성화 시 timer가 정리되는지 검증한다.

## 제외 범위

- Slack end-to-end exactly-once outbox
- TaskRun 결과 JSON 검색
- 완료 이력 필터와 상세 화면
- 운영·개발 DB의 관리자 이름 일괄 변환
