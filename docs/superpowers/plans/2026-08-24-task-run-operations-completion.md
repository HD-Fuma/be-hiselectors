# TaskRun 운영 보완 구현 계획

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TaskRun 실패 알림, 정산 세부 결과, 완료 이력 화면, 1초 폴링, 로컬 관리자 데이터 교정을 완료한다.

**Architecture:** 기존 `BatchEventLogger`와 CloudWatch/Lambda/SNS 경로, TaskRun `progress_message`, `/api/admin/task-runs/recent` API를 그대로 재사용한다. 새 DB 컬럼·outbox·상세 API를 만들지 않고 terminal 전이 직후의 immutable snapshot과 기존 프론트 공용 컴포넌트만 사용한다.

**Tech Stack:** Java 21, Spring Boot 4, JPA/MySQL, JUnit 5/Mockito, Python unittest, React 19, TypeScript, Vitest, Vite

---

## Chunk 1: 백엔드 운영 결과

### Task 1: TaskRun 실패 구조화 로그·Slack

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/logging/BatchEventLogger.java`
- Create: `src/main/java/com/fuma/hiselectors/taskrun/logging/TaskRunFailureLogger.java`
- Create: `src/main/java/com/fuma/hiselectors/taskrun/service/TaskRunTerminalSnapshot.java`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/service/TaskRunService.java`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/service/TaskRunExecutionService.java`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/scheduler/StaleTaskRunScheduler.java`
- Test: `src/test/java/com/fuma/hiselectors/logging/BatchEventLoggerTest.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/service/TaskRunServiceTest.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/service/TaskRunExecutionServiceTest.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/scheduler/StaleTaskRunSchedulerTest.java`
- Review and modify only these existing untracked, in-scope Slack files; never stage the whole `ops/` tree:
  - `ops/lambda/batch-log-to-slack/lambda_function.py`
  - `ops/lambda/batch-log-to-slack/test_lambda_function.py`
  - `ops/lambda/batch-log-to-slack/README.md`
  - `ops/lambda/batch-log-to-slack/iam-policy.json`

- [ ] Add failing tests for exact TaskRun `runId`, status-specific fallback error, optional `counts.total`, task/trigger details, and 500-character error bound.
- [ ] Run focused logger tests and confirm expected failures.
- [ ] Add immutable `TaskRunTerminalSnapshot`; change `complete`, `fail`, and `failQueued` to return it only after successful transition/flush, with tests for all fields and invalid transition/lease loss.
- [ ] Add `TaskRunFailureLogger` that accepts only `PARTIAL_FAILED`, `FAILED`, `STALE` terminal snapshots and delegates to the existing JSON logger.
- [ ] Add failing execution service tests for normal failed completion, partial failure, exception failure, executor rejection, success suppression, lease-loss suppression, and logger exception isolation.
- [ ] Add failing stale scheduler tests proving only a committed stale transition logs once per process and candidates that disappear/become fresh/terminal do not log.
- [ ] Call the logger after `complete`, `fail`, and `failQueued` transactions return; return a snapshot from the stale transaction and log after `TransactionTemplate.execute` returns.
- [ ] For normal completion/failure, executor rejection, and stale, assert logger exceptions cannot roll back the committed state.
- [ ] Inspect and preserve the four untracked Slack files, then add failing Python tests for `PARTIAL_FAILED`, `STALE`, `taskType`, actual `runId`, title/body, and existing `PARTIAL_FAILURE`.
- [ ] Update `lambda_function.py` validation/title/body mapping minimally; update README/IAM only if the code contract actually requires it.
- [ ] Run `./gradlew test --tests 'com.fuma.hiselectors.logging.BatchEventLoggerTest' --tests 'com.fuma.hiselectors.taskrun.service.TaskRunServiceTest' --tests 'com.fuma.hiselectors.taskrun.service.TaskRunExecutionServiceTest' --tests 'com.fuma.hiselectors.taskrun.scheduler.StaleTaskRunSchedulerTest'` from `/Users/leeyukyung/project/be-hiselectors`.
- [ ] Run `python3 -m unittest ops/lambda/batch-log-to-slack/test_lambda_function.py` from `/Users/leeyukyung/project/be-hiselectors`.
- [ ] Commit as `Feat: TaskRun 실패 로그와 Slack 알림 연동`.

### Task 2: 정산 재계산 세부 결과 저장

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/settlement/task/SettlementRecalculationTask.java`
- Test: `src/test/java/com/fuma/hiselectors/settlement/task/SettlementTaskTest.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/service/TaskRunExecutionServiceTest.java`

- [ ] Add a failing test expecting `describe("신규 1건 · 수정 2건 · 확정 1건 · 실패 1건 · 건너뜀 1건")` before aggregate progress advance.
- [ ] Run the focused test and confirm the missing `describe` failure.
- [ ] Add the single final `describe` call using existing response counts.
- [ ] Add an execution integration test that runs the settlement task and reads the terminal row, asserting exact message and aggregate counts for threshold-triggered and final-flush paths.
- [ ] Run `./gradlew test --tests 'com.fuma.hiselectors.settlement.task.SettlementTaskTest' --tests 'com.fuma.hiselectors.taskrun.service.TaskRunExecutionServiceTest'` from `/Users/leeyukyung/project/be-hiselectors`.
- [ ] Commit as `Feat: 정산 재계산 세부 결과 기록`.

## Chunk 2: 관리자 완료 이력

### Task 3: 완료 이력 화면과 1초 폴링

**Files:**
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/entities/task-run/model.ts`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/entities/task-run/api.ts`
- Test: `/Users/leeyukyung/project/fe-hiselectors-admin/src/entities/task-run/api.test.ts`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/entities/task-run/index.ts`
- Create: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/TaskRunHistoryPage.tsx`
- Create: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/TaskRunHistoryPage.test.tsx`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/taskRunPresentation.ts`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/useTaskRunPanel.ts`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/useTaskRunPanel.test.tsx`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/app/navigation.ts`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/components/shell/navigationModel.ts`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/components/shell/AdminSidebar.tsx`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/components/shell/AppShell.test.tsx`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/test/renderRoute.tsx`
- Update typed TaskRun fixtures in:
  - `src/entities/content/api.test.ts`
  - `src/features/notifications/NotificationPages.test.tsx`
  - `src/features/creators/CreatorFilters.test.tsx`
  - `src/features/task-runs/TaskRunFloatingPanel.test.tsx`
  - `src/features/task-runs/TaskRunPanelHost.test.tsx`
  - `src/features/task-runs/taskRunPreview.ts`
  - `src/features/task-runs/taskRunPreview.test.ts`
  - `src/features/task-runs/useTaskRunPanel.test.tsx`
  - `src/components/shell/AppShell.test.tsx`

- [ ] Add failing API tests for `ApiResult<SpringPage<TaskRun>>`, nullable timestamps/name, envelope validation, and `page=n-1&size=20` request mapping.
- [ ] Implement `getRecentTaskRuns` and the minimal history page model.
- [ ] Add failing page tests for loading, empty, error, scheduled/admin subject, null/blank administrator names, null administrator, null start time, nonblank/blank `progressMessage` on non-creator tasks, count fallback, and pagination.
- [ ] Implement `/task-runs` using existing `PageHeader`, `ResultToolbar`, `DenseTable`, `Pagination`, and `StatusPill` without new CSS.
- [ ] Add route/sidebar tests and the `작업 > 실행 이력` navigation entry.
- [ ] Change the polling test boundary from `1999/1` to `999/1`, confirm RED, then change the implementation timeout from `2000` to `1000`.
- [ ] Run focused tests from `/Users/leeyukyung/project/fe-hiselectors-admin`, then `npm run lint`, `npm run test:run`, and `npm run build` in that repository.
- [ ] Commit in the frontend repository with `git -C /Users/leeyukyung/project/fe-hiselectors-admin add <exact files>` and message `Feat: TaskRun 완료 이력과 1초 폴링 추가`.

## Chunk 3: 로컬 통합 검증

### Task 4: 로컬 관리자 이름 데이터 교정

**Files:**
- No repository file changes.
- Local DB only: `hiselectors_taskrun_local.admin`.

- [ ] Verify `login_id='admin1'` matches exactly one row and capture current `HEX(name)`.
- [ ] Stop unless `SELECT COUNT(*) FROM hiselectors_taskrun_local.admin WHERE login_id='admin1'` is exactly `1`.
- [ ] Update only that row with known UTF-8 bytes `EBA19CECBBAC20EAB480EBA6ACEC9E90` and verify `ROW_COUNT() = 1`.
- [ ] Verify database-qualified `name`, `HEX(name)`, and the login response.
- [ ] Do not create a migration or commit.

### Task 5: 통합·회귀 검증

**Files:**
- No new production files expected.

- [ ] Restart backend against `hiselectors_taskrun_local` with `ddl-auto=validate`, all cron properties set to `-`, analyzer/default discovery disabled, and application media/analysis initial delays set beyond the test window; verify `/actuator/scheduledtasks` instead of claiming every scheduler bean is disabled.
- [ ] Start frontend on `127.0.0.1:5175` with `VITE_API_BASE_URL=http://127.0.0.1:8080`.
- [ ] Verify login, `/task-runs`, panel polling, empty history, and console/network errors.
- [ ] Execute a safe local TaskRun path or seed only the clone when needed; do not call email/Kakao external delivery.
- [ ] Run `git diff --check`, inspect tracked/untracked ownership, and report exact test results and remaining environmental failures.
- [ ] Dispatch final spec and code-quality reviews; fix and re-run until approved.
