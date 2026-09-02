package com.fuma.hiselectors.taskrun.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 배치 실행 한 건의 상태와 진행률을 보관한다.
 * 상태는 {@code QUEUED -> RUNNING -> 종료 상태} 순서로만 변경된다.
 */
@Entity
@Table(name = "task_run",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_task_run_run_id", columnNames = "run_id"),
                @UniqueConstraint(name = "uq_task_run_concurrency_key", columnNames = "concurrency_key"),
                @UniqueConstraint(name = "uq_task_run_idempotency_key", columnNames = "idempotency_key")
        },
        indexes = {
                @Index(name = "idx_task_run_status_started_at", columnList = "status, started_at"),
                @Index(name = "idx_task_run_finished_at", columnList = "finished_at"),
                @Index(name = "idx_task_run_queue_pending",
                        columnList = "queue_managed, status, queue_available_at, last_enqueued_at"),
                @Index(name = "idx_task_run_queue_heartbeat", columnList = "queue_managed, heartbeat_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_run_id")
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "run_id", nullable = false, updatable = false, length = 36)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, updatable = false, length = 40)
    private TaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskRunStatus status;

    @Column(name = "current_step", length = 255)
    private String currentStep;

    @Column(name = "progress_message", length = 500)
    private String progressMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_progress")
    private Map<String, TaskStepProgress> stepProgress;

    @Column(name = "total_count")
    private Long totalCount;

    @Column(name = "processed_count", nullable = false)
    private long processedCount;

    @Column(name = "succeeded_count", nullable = false)
    private long succeededCount;

    @Column(name = "failed_count", nullable = false)
    private long failedCount;

    @Column(name = "skipped_count", nullable = false)
    private long skippedCount;

    @Column(name = "started_by_admin_id", updatable = false)
    private Long startedByAdminId;

    /** 동시에 하나만 허용하는 작업의 중복 실행 방지 키. 종료 시 해제한다. */
    @Column(name = "concurrency_key", length = 191)
    private String concurrencyKey;

    /** 동일한 요청이 재전송돼도 실행을 한 번만 생성하기 위한 키. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 36)
    private UUID idempotencyKey;

    /** 작업 종류와 요청 내용의 SHA-256 해시. 같은 멱등 키의 내용 변경을 감지한다. */
    @Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
    private String requestFingerprint;

    /** Queue messages contain only runId; the validated command remains in the database. */
    @Column(name = "business_payload", columnDefinition = "TEXT")
    private String businessPayload;

    @Column(name = "queue_managed", nullable = false)
    private boolean queueManaged;

    @Column(name = "queue_attempts", nullable = false)
    private int queueAttempts;

    @Column(name = "queue_available_at")
    private Instant queueAvailableAt;

    @Column(name = "queue_lease_until")
    private Instant queueLeaseUntil;

    @Column(name = "last_enqueued_at")
    private Instant lastEnqueuedAt;

    /** 현재 작업자의 소유권 토큰. 교체되면 이전 작업자는 더 이상 상태를 기록할 수 없다. */
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "lease_token", length = 36)
    private UUID leaseToken;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Version
    @Column(nullable = false)
    private long version;

    public static TaskRun queued(
            TaskType type,
            TriggerType trigger,
            Long adminId,
            UUID idempotencyKey,
            String fingerprint,
            String concurrencyKey,
            Instant now) {
        Objects.requireNonNull(type, "작업 유형은 필수입니다.");
        Objects.requireNonNull(trigger, "실행 출처는 필수입니다.");
        Objects.requireNonNull(idempotencyKey, "멱등 키는 필수입니다.");
        Objects.requireNonNull(fingerprint, "요청 지문은 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        if (trigger == TriggerType.ADMIN_TRIGGERED && adminId == null) {
            throw new IllegalArgumentException("관리자 실행에는 관리자 ID가 필요합니다.");
        }
        if (trigger == TriggerType.SCHEDULED && adminId != null) {
            throw new IllegalArgumentException("스케줄 실행에는 관리자 ID를 지정할 수 없습니다.");
        }

        TaskRun run = new TaskRun();
        run.runId = UUID.randomUUID();
        run.taskType = type;
        run.triggerType = trigger;
        run.status = TaskRunStatus.QUEUED;
        run.startedByAdminId = adminId;
        run.idempotencyKey = idempotencyKey;
        run.requestFingerprint = fingerprint;
        run.concurrencyKey = concurrencyKey;
        run.heartbeatAt = now;
        return run;
    }

    /** 대기 중인 실행을 시작하면서 현재 작업자만 사용할 수 있는 리스를 발급한다. */
    public void markRunning(UUID newLeaseToken, Instant now) {
        requireStatus(TaskRunStatus.QUEUED);
        Objects.requireNonNull(newLeaseToken, "리스 토큰은 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        this.status = TaskRunStatus.RUNNING;
        this.leaseToken = newLeaseToken;
        this.startedAt = now;
        this.heartbeatAt = now;
    }

    public void enableQueue(String payload, Instant now) {
        requireStatus(TaskRunStatus.QUEUED);
        Objects.requireNonNull(payload, "Queue command is required");
        if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60_000) {
            throw new IllegalArgumentException("Queue command exceeds 60 KB");
        }
        this.businessPayload = payload;
        this.queueManaged = true;
        this.queueAvailableAt = Objects.requireNonNull(now);
    }

    public void recordEnqueued(Instant now) {
        this.lastEnqueuedAt = Objects.requireNonNull(now);
    }

    /** Called under a row lock, after checking an expired lease and retry safety. */
    public void startQueueAttempt(UUID token, Instant now, Instant leaseUntil) {
        requireActive();
        if (!queueManaged) {
            throw new IllegalStateException("Not a queue-managed run");
        }
        this.status = TaskRunStatus.QUEUED;
        this.currentStep = null;
        this.progressMessage = null;
        this.stepProgress = null;
        this.totalCount = null;
        this.processedCount = 0;
        this.succeededCount = 0;
        this.failedCount = 0;
        this.skippedCount = 0;
        this.finishedAt = null;
        this.errorType = null;
        this.errorMessage = null;
        this.queueAttempts++;
        this.queueLeaseUntil = Objects.requireNonNull(leaseUntil);
        markRunning(token, now);
    }

    public void renewQueueLease(Instant now, Instant leaseUntil) {
        requireRunning();
        this.queueLeaseUntil = Objects.requireNonNull(leaseUntil);
        touch(now);
    }

    public void retryQueue(Instant now, Instant availableAt, String errorType, String errorMessage) {
        requireRunning();
        recordFailure(errorType, errorMessage, now);
        this.status = TaskRunStatus.QUEUED;
        this.queueAvailableAt = Objects.requireNonNull(availableAt);
        this.queueLeaseUntil = null;
        this.leaseToken = null;
        // Keep the business concurrency key until the final attempt has finished.
        touch(now);
    }

    public void setTotal(long total, Instant now) {
        requireRunning();
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        if (total < 0) {
            throw new IllegalArgumentException("전체 건수는 음수일 수 없습니다.");
        }
        if (total < processedCount) {
            throw new IllegalStateException("전체 건수는 이미 처리한 건수보다 작을 수 없습니다.");
        }
        this.totalCount = total;
        touch(now);
    }

    public void changeStep(String step, Instant now) {
        requireRunning();
        Objects.requireNonNull(step, "현재 단계는 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        this.currentStep = step;
        touch(now);
    }

    public void changeProgressMessage(String message, Instant now) {
        requireRunning();
        Objects.requireNonNull(message, "진행 메시지는 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        if (message.length() > 500) {
            throw new IllegalArgumentException("진행 메시지는 500자를 초과할 수 없습니다.");
        }
        this.progressMessage = message;
        touch(now);
    }

    /** 작업을 계속할 수 있는 항목 단위 실패의 최신 안전 요약을 기록한다. */
    public void recordFailure(String type, String message, Instant now) {
        requireRunning();
        Objects.requireNonNull(type, "오류 유형은 필수입니다.");
        Objects.requireNonNull(message, "오류 메시지는 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        if (type.isBlank()) {
            throw new IllegalArgumentException("오류 유형은 비어 있을 수 없습니다.");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("오류 메시지는 비어 있을 수 없습니다.");
        }
        if (type.length() > 100) {
            throw new IllegalArgumentException("오류 유형은 100자를 초과할 수 없습니다.");
        }
        if (message.length() > 500) {
            throw new IllegalArgumentException("오류 메시지는 500자를 초과할 수 없습니다.");
        }
        this.errorType = type;
        this.errorMessage = message;
        touch(now);
    }

    public void mergeStepProgress(Map<String, TaskStepProgress> patch, Instant now) {
        requireRunning();
        Objects.requireNonNull(patch, "단계 진행률 변경값은 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        LinkedHashMap<String, TaskStepProgress> merged = new LinkedHashMap<>();
        if (stepProgress != null) {
            merged.putAll(stepProgress);
        }
        patch.forEach((stepKey, progress) -> {
            requireValidStepKey(stepKey);
            merged.put(stepKey, Objects.requireNonNull(progress, "단계 진행률은 필수입니다."));
        });
        this.stepProgress = Collections.unmodifiableMap(merged);
        touch(now);
    }

    public Map<String, TaskStepProgress> getStepProgress() {
        if (stepProgress == null) {
            return null;
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(stepProgress));
    }

    public void addCounts(long succeeded, long failed, long skipped, Instant now) {
        requireRunning();
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        requireNonNegative("성공 건수", succeeded);
        requireNonNegative("실패 건수", failed);
        requireNonNegative("건너뜀 건수", skipped);

        long nextSucceeded = Math.addExact(succeededCount, succeeded);
        long nextFailed = Math.addExact(failedCount, failed);
        long nextSkipped = Math.addExact(skippedCount, skipped);
        // 처리 건수는 별도로 증가시키지 않고 세 결과의 합으로 계산해 항상 정합성을 유지한다.
        long nextProcessed = Math.addExact(Math.addExact(nextSucceeded, nextFailed), nextSkipped);
        if (totalCount != null && nextProcessed > totalCount) {
            throw new IllegalStateException("처리 건수는 전체 건수를 초과할 수 없습니다.");
        }

        this.succeededCount = nextSucceeded;
        this.failedCount = nextFailed;
        this.skippedCount = nextSkipped;
        this.processedCount = nextProcessed;
        touch(now);
    }

    public void complete(Instant now) {
        requireRunning();
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        // 실패만 있으면 실패, 성공과 실패가 섞이면 부분 실패, 실패가 없으면 성공이다.
        if (failedCount > 0 && succeededCount == 0) {
            this.status = TaskRunStatus.FAILED;
        } else if (failedCount > 0) {
            this.status = TaskRunStatus.PARTIAL_FAILED;
        } else {
            this.status = TaskRunStatus.SUCCEEDED;
        }
        finish(now, true);
    }

    public void fail(String errorType, String boundedMessage, Instant now) {
        requireActive();
        Objects.requireNonNull(errorType, "오류 유형은 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        this.status = TaskRunStatus.FAILED;
        this.errorType = errorType;
        this.errorMessage = boundedMessage;
        finish(now, true);
    }

    public void markStale(UUID replacementToken, boolean clearConcurrencyKey, Instant now) {
        requireActive();
        Objects.requireNonNull(replacementToken, "대체 리스 토큰은 필수입니다.");
        Objects.requireNonNull(now, "기준 시각은 필수입니다.");
        // 리스를 먼저 교체해 이전 작업자를 차단하고, 안전할 때만 중복 실행 방지 키를 해제한다.
        this.status = TaskRunStatus.STALE;
        this.leaseToken = replacementToken;
        finish(now, clearConcurrencyKey);
    }

    public boolean isTerminal() {
        return status == TaskRunStatus.SUCCEEDED
                || status == TaskRunStatus.PARTIAL_FAILED
                || status == TaskRunStatus.FAILED
                || status == TaskRunStatus.STALE;
    }

    private void requireRunning() {
        requireStatus(TaskRunStatus.RUNNING);
    }

    private void requireActive() {
        if (status != TaskRunStatus.QUEUED && status != TaskRunStatus.RUNNING) {
            throw new IllegalStateException("종료된 작업 실행은 변경할 수 없습니다.");
        }
    }

    private void requireStatus(TaskRunStatus expected) {
        if (status != expected) {
            String expectedStatus = expected == TaskRunStatus.QUEUED ? "대기 중" : "실행 중";
            throw new IllegalStateException("작업 실행 상태는 " + expectedStatus + "이어야 합니다.");
        }
    }

    private void requireNonNegative(String name, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + "는 음수일 수 없습니다.");
        }
    }

    private void requireValidStepKey(String stepKey) {
        if (stepKey == null || stepKey.isBlank()) {
            throw new IllegalArgumentException("단계 키는 비어 있을 수 없습니다.");
        }
        if (stepKey.length() > 100) {
            throw new IllegalArgumentException("단계 키는 100자를 초과할 수 없습니다.");
        }
    }

    private void touch(Instant now) {
        this.heartbeatAt = now;
    }

    private void finish(Instant now, boolean clearConcurrencyKey) {
        this.finishedAt = now;
        this.heartbeatAt = now;
        if (clearConcurrencyKey) {
            this.concurrencyKey = null;
        }
    }
}
