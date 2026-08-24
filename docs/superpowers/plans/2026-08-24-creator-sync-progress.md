# Creator Sync Progress Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show live, platform-specific creator collection counts without adding YouTube keywords and Instagram creators into one total.

**Architecture:** Reuse the existing sequential discovery loops and add optional progress snapshot callbacks. Keep unique creator IDs in memory for one execution, persist one generic `progress_message` on TaskRun through the existing throttled reporter, and let the frontend display that message for CREATOR_SYNC instead of generic mixed-unit counters.

**Tech Stack:** Java 17, Spring Boot, JPA/MySQL, JUnit 5/Mockito, React/TypeScript, Vitest.

---

## Chunk 1: Backend collection snapshots

### Task 1: Return committed YouTube creator IDs

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/creator/discovery/DiscoveryPipelineService.java`
- Modify: `src/main/java/com/fuma/hiselectors/creator/discovery/dto/DiscoveryRunResult.java`
- Test: `src/test/java/com/fuma/hiselectors/creator/discovery/DiscoveryPipelineServiceTest.java`

- [ ] Add failing tests proving a successful keyword result contains the IDs of saved or updated creators and excludes skipped channels, and a transaction failure returns no result/IDs to its caller.
- [ ] Run `./gradlew test --tests '*DiscoveryPipelineServiceTest' --no-daemon`; expect compilation/test failure because committed IDs are absent.
- [ ] Change the transaction-local save outcome to carry `creator.getId()`, collect IDs inside `persistDiscoveryResult`, and return them only after the transaction succeeds. Keep the public JSON response unchanged with `@JsonIgnore` on the internal ID set.
- [ ] Re-run the focused test and expect PASS.

### Task 2: Emit deduplicated platform snapshots

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/creator/discovery/scheduler/YoutubeDiscoveryBatchResult.java`
- Modify: `src/main/java/com/fuma/hiselectors/creator/discovery/scheduler/YoutubeDiscoveryBatchService.java`
- Modify: `src/main/java/com/fuma/hiselectors/creator/discovery/batch/InstagramDiscoveryBatchResult.java`
- Modify: `src/main/java/com/fuma/hiselectors/creator/discovery/batch/InstagramDiscoveryBatchService.java`
- Test: `src/test/java/com/fuma/hiselectors/creator/discovery/scheduler/YoutubeDiscoveryBatchServiceTest.java`
- Test: `src/test/java/com/fuma/hiselectors/creator/discovery/batch/InstagramDiscoveryBatchServiceTest.java`

- [ ] Add failing tests that call new `runYoutubeOnly(Consumer<...>)` and `run(Consumer<...>)` overloads, assert one snapshot after every attempted item, use quota/blank-handle-filtered `targetKeywords`/`targetCreators` totals, count repeated creator IDs once, and do not increase the unique count after a failed keyword/item.
- [ ] Run both focused test classes; expect compilation failure for missing overloads/count fields.
- [ ] Add no-arg methods delegating to no-op callbacks. Maintain a local `Set<Long>` per execution, add IDs only from successful results, and include `targetKeywords` or `targetCreators` plus `uniqueCollectedCreators` in each result snapshot.
- [ ] Re-run both focused test classes and expect PASS.

## Chunk 2: TaskRun progress message

### Task 3: Persist a throttled generic progress message

**Files:**
- Modify: `docs/task-run-migration.sql`
- Create: `docs/task-run-progress-message-migration.sql`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/model/TaskRun.java`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/service/TaskProgressReporter.java`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/service/ThrottledTaskProgressReporter.java`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/service/TaskLeaseTransaction.java`
- Modify: `src/main/java/com/fuma/hiselectors/taskrun/dto/TaskRunResponse.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/model/TaskRunTest.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/service/TaskProgressReporterTest.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/service/TaskRunExecutionServiceTest.java`
- Test: `src/test/java/com/fuma/hiselectors/taskrun/controller/TaskRunAdminControllerTest.java`

- [ ] Add failing tests for a nullable 500-character-bounded message, a pending `describe(message)` saved with the next count flush, a message-only final `flush()`, execution-service persistence of the final message before `complete()`, and the `progressMessage` API field.
- [ ] Run the focused TaskRun tests; expect compilation failures for the missing message API.
- [ ] Add nullable `progress_message VARCHAR(500)`, a one-time MySQL `ALTER TABLE task_run ADD COLUMN progress_message VARCHAR(500) NULL` migration for existing databases, `TaskRun.changeProgressMessage`, reporter `describe`, pending-message flush, transaction persistence, and the response field. Reject overlength text with a Korean domain exception message.
- [ ] Re-run the focused TaskRun tests and expect PASS.

### Task 4: Format creator progress from snapshots

**Files:**
- Modify: `src/main/java/com/fuma/hiselectors/creator/task/CreatorSyncTask.java`
- Modify: `src/main/java/com/fuma/hiselectors/creator/task/InstagramCreatorSyncTask.java`
- Test: `src/test/java/com/fuma/hiselectors/creator/task/CreatorSyncTaskTest.java`
- Test: `src/test/java/com/fuma/hiselectors/creator/task/InstagramCreatorSyncTaskTest.java`

- [ ] Add failing tests for `2개 키워드 중 1개 처리 · 크리에이터 7명 수집`, `4명 중 3명 처리 · 크리에이터 3명 수집`, combined terminal `YouTube 7명 · Instagram 4명 수집`, and Instagram-only terminal `Instagram 4명 수집` messages. Assert common counts still drive terminal status but no combined total is set.
- [ ] Run both task tests; expect failure because callbacks/descriptions are unused.
- [ ] Wire snapshot callbacks to `progress.describe(...)`. Track the previous snapshot and pass only per-item success/failure deltas to `advance(...)`, never cumulative values; write the final platform message before coordinator final flush.
- [ ] Re-run both tests and expect PASS.

## Chunk 3: Frontend presentation

### Task 5: Render the backend progress message

**Files:**
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/entities/task-run/model.ts`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/TaskRunCard.tsx`
- Modify: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/taskRunPresentation.ts`
- Test: `/Users/leeyukyung/project/fe-hiselectors-admin/src/features/task-runs/TaskRunFloatingPanel.test.tsx`

- [ ] Add failing UI tests proving active and terminal CREATOR_SYNC cards render `progressMessage`, hide generic counts/percentage, and other task types remain unchanged.
- [ ] Run `npm test -- --run src/features/task-runs/TaskRunFloatingPanel.test.tsx`; expect failures for the missing field/rendering.
- [ ] Add nullable `progressMessage` to the model. Prefer it for CREATOR_SYNC active/terminal text and suppress generic progress/failure-number UI only for that task type.
- [ ] Re-run the focused UI test and expect PASS.

## Chunk 4: Verification and history cleanup

### Task 6: Verify and fold into the original feature commit

- [ ] Run backend focused tests for discovery, creator tasks, TaskRun model/reporter/query/controller.
- [ ] Run the full backend test suite and `git diff --check`.
- [ ] Run frontend focused tests, lint, build, and full tests; report any pre-existing unrelated failure separately.
- [ ] Update the Notion commit-review page with the corrected count semantics.
- [ ] Before committing, report all changed paths and proposed message `Feat: 크리에이터 동기화를 TaskRun 요청으로 전환`.
- [ ] Fold BE implementation, design, and plan into commit `02fd036`; ensure the final TaskRun milestone remains 14 commits.
- [ ] Fold FE changes into `f445afa` and rename it to `Feat: 크리에이터 TaskRun 진행 상황 표시`; ensure both tracked worktrees are clean.
