package com.fuma.hiselectors.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fuma.hiselectors.taskrun.logging.TaskRunFailureLogger;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunTerminalSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class BatchEventLoggerTest {

    private static final String PREFIX = "BATCH_EVENT ";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-08-22T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));
    private final Logger log = (Logger) LoggerFactory.getLogger(BatchEventLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private BatchEventLogger batchEventLogger;

    @BeforeEach
    void setUp() {
        appender.start();
        log.addAppender(appender);
        batchEventLogger = new BatchEventLogger(objectMapper, clock);
    }

    @AfterEach
    void tearDown() {
        log.detachAppender(appender);
        appender.stop();
    }

    @Test
    void startAndSuccessEmitVersionedCorrelatedEvents() throws Exception {
        BatchLogContext run = batchEventLogger.start("content-sync");
        clock.advance(Duration.ofMillis(1_250));

        batchEventLogger.succeeded(
                run,
                Map.of("targets", 4L, "processed", 4L),
                Map.of("source", "scheduler"));

        List<JsonNode> events = events();
        assertThat(events).hasSize(2);

        JsonNode started = events.get(0);
        assertThat(started.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(started.get("event").asText()).isEqualTo("BATCH_RUN");
        assertThat(started.get("batch").asText()).isEqualTo("content-sync");
        assertThat(started.get("status").asText()).isEqualTo("STARTED");
        assertThat(OffsetDateTime.parse(started.get("timestamp").asText()).toInstant())
                .isEqualTo(Instant.parse("2026-08-22T03:00:00Z"));
        assertThat(OffsetDateTime.parse(started.get("timestamp").asText()).getOffset())
                .isEqualTo(ZoneOffset.ofHours(9));
        assertThatCodeIsUuid(started.get("runId").asText());
        assertThat(started.has("durationMs")).isFalse();
        assertThat(started.has("counts")).isFalse();
        assertThat(started.has("details")).isFalse();
        assertThat(started.has("reason")).isFalse();
        assertThat(started.has("error")).isFalse();

        JsonNode succeeded = events.get(1);
        assertThat(succeeded.get("runId").asText()).isEqualTo(started.get("runId").asText());
        assertThat(succeeded.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(OffsetDateTime.parse(succeeded.get("timestamp").asText()).toInstant())
                .isEqualTo(Instant.parse("2026-08-22T03:00:01.250Z"));
        assertThat(succeeded.get("durationMs").asLong()).isEqualTo(1_250L);
        assertThat(succeeded.get("counts").get("targets").asLong()).isEqualTo(4L);
        assertThat(succeeded.get("counts").get("processed").asLong()).isEqualTo(4L);
        assertThat(succeeded.get("details").get("source").asText()).isEqualTo("scheduler");
        assertThat(succeeded.has("reason")).isFalse();
        assertThat(succeeded.has("error")).isFalse();
    }

    @Test
    void failureDoesNotExposeThrowableMessageOrStackTrace() throws Exception {
        BatchLogContext run = batchEventLogger.start("settlement-payment");
        String secretMessage = "TOKEN=sentinel-secret\ncustomer@example.com";

        batchEventLogger.failed(run, new IllegalStateException(secretMessage));

        JsonNode failed = events().get(1);
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        assertThat(failed.get("error").size()).isEqualTo(2);
        assertThat(failed.get("error").get("type").asText()).isEqualTo("IllegalStateException");
        assertThat(failed.get("error").get("message").asText())
                .isEqualTo("Batch execution failed");
        assertThat(failed.toString())
                .doesNotContain(
                        "sentinel-secret",
                        "customer@example.com",
                        "stackTrace",
                        "BatchEventLoggerTest.java");
    }

    @Test
    void partialFailureAndSkippedCarryTheirOptionalData() throws Exception {
        BatchLogContext partial = batchEventLogger.start("settlement-expiry");
        batchEventLogger.partialFailure(partial, Map.of("failed", 2L), Map.of("retryable", true));

        BatchLogContext skipped = batchEventLogger.start("purchase-auto-confirmation");
        batchEventLogger.skipped(
                skipped,
                "NO_TARGETS",
                Map.of("confirmed", 0L),
                Map.of("polling", true));

        List<JsonNode> events = events();
        assertThat(events.get(1).get("status").asText()).isEqualTo("PARTIAL_FAILURE");
        assertThat(events.get(1).get("counts").get("failed").asLong()).isEqualTo(2L);
        assertThat(events.get(1).get("details").get("retryable").asBoolean()).isTrue();
        assertThat(events.get(3).get("status").asText()).isEqualTo("SKIPPED");
        assertThat(events.get(3).get("reason").asText()).isEqualTo("NO_TARGETS");
        assertThat(events.get(3).get("counts").get("confirmed").asLong()).isZero();
        assertThat(events.get(3).get("details").get("polling").asBoolean()).isTrue();
    }

    @Test
    void taskRunTerminalUsesTheActualRunIdAndTerminalSnapshotTime() throws Exception {
        UUID runId = UUID.fromString("2284fbed-2d99-422f-a18e-e875055fcb38");
        Instant startedAt = Instant.parse("2026-08-22T02:59:58Z");
        Instant finishedAt = Instant.parse("2026-08-22T03:00:00Z");

        batchEventLogger.taskRunTerminal(
                runId,
                "PARTIAL_FAILED",
                startedAt,
                finishedAt,
                Map.of(
                        "total", 5L,
                        "processed", 4L,
                        "succeeded", 2L,
                        "failed", 1L,
                        "skipped", 1L),
                Map.of("taskType", "CONTENT_SYNC", "triggerType", "SCHEDULED"),
                "TASK_RUN_PARTIAL_FAILED",
                "일부 처리 항목이 실패했습니다.");

        JsonNode event = events().getFirst();
        assertThat(event.get("batch").asText()).isEqualTo("task-run");
        assertThat(event.get("runId").asText()).isEqualTo(runId.toString());
        assertThat(event.get("status").asText()).isEqualTo("PARTIAL_FAILED");
        assertThat(OffsetDateTime.parse(event.get("timestamp").asText()).toInstant())
                .isEqualTo(finishedAt);
        assertThat(event.get("durationMs").asLong()).isEqualTo(2_000L);
        assertThat(event.get("counts").get("total").asLong()).isEqualTo(5L);
        assertThat(event.get("counts").get("processed").asLong()).isEqualTo(4L);
        assertThat(event.get("details").get("taskType").asText()).isEqualTo("CONTENT_SYNC");
        assertThat(event.get("details").get("triggerType").asText()).isEqualTo("SCHEDULED");
        assertThat(event.get("error").get("type").asText()).isEqualTo("TASK_RUN_PARTIAL_FAILED");
        assertThat(event.get("error").get("message").asText())
                .isEqualTo("일부 처리 항목이 실패했습니다.");
    }

    @Test
    void taskRunTerminalOmitsNullTotalBoundsErrorAndPreventsNegativeDuration() throws Exception {
        batchEventLogger.taskRunTerminal(
                UUID.fromString("e44282da-2300-49f8-a281-d107f7344f11"),
                "STALE",
                Instant.parse("2026-08-22T03:00:01Z"),
                Instant.parse("2026-08-22T03:00:00Z"),
                Map.of("processed", 0L, "succeeded", 0L, "failed", 0L, "skipped", 0L),
                Map.of("taskType", "CREATOR_SYNC", "triggerType", "SCHEDULED"),
                "TASK_RUN_STALE",
                "가".repeat(600));

        JsonNode event = events().getFirst();
        assertThat(event.get("durationMs").asLong()).isZero();
        assertThat(event.get("counts").has("total")).isFalse();
        assertThat(event.get("error").get("message").asText()).hasSize(500);
    }

    @Test
    void taskRunFailureLoggerSuppliesStatusSpecificNonblankFallbackErrors() {
        TaskRunFailureLogger failureLogger = new TaskRunFailureLogger(batchEventLogger);

        failureLogger.log(snapshot(TaskRunStatus.FAILED, " ", null));
        failureLogger.log(snapshot(TaskRunStatus.PARTIAL_FAILED, null, ""));
        failureLogger.log(snapshot(TaskRunStatus.STALE, "", " "));

        assertThat(events()).extracting(event -> event.get("error").get("type").asText())
                .containsExactly("TASK_RUN_FAILED", "TASK_RUN_PARTIAL_FAILED", "TASK_RUN_STALE");
        assertThat(events()).extracting(event -> event.get("error").get("message").asText())
                .containsExactly(
                        "처리 결과에 실패 건수가 포함되어 있습니다.",
                        "일부 처리 항목이 실패했습니다.",
                        "제한 시간 동안 heartbeat가 없어 비정상 종료로 판정했습니다.");
    }

    @Test
    void taskRunFailureLoggerRejectsSucceededSnapshots() {
        TaskRunFailureLogger failureLogger = new TaskRunFailureLogger(batchEventLogger);

        assertThatThrownBy(() -> failureLogger.log(snapshot(TaskRunStatus.SUCCEEDED, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void rejectsInvalidBatchNamesAndDuplicateTerminalEvents() {
        assertThatThrownBy(() -> batchEventLogger.start("Content Sync"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> batchEventLogger.start("content_sync"))
                .isInstanceOf(IllegalArgumentException.class);

        BatchLogContext run = batchEventLogger.start("content-sync");
        batchEventLogger.succeeded(run, Map.of(), Map.of());

        assertThatThrownBy(() -> batchEventLogger.skipped(run, "NO_TARGETS", Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(appender.list).hasSize(2);
    }

    @Test
    void rejectsUnsafeCountsAndDetails() {
        BatchLogContext negativeCount = batchEventLogger.start("content-sync");
        assertThatThrownBy(() -> batchEventLogger.succeeded(
                        negativeCount, Map.of("failed", -1L), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        BatchLogContext invalidKey = batchEventLogger.start("content-sync");
        assertThatThrownBy(() -> batchEventLogger.succeeded(
                        invalidKey, Map.of("bad key", 1L), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        BatchLogContext objectGraph = batchEventLogger.start("content-sync");
        assertThatThrownBy(() -> batchEventLogger.succeeded(
                        objectGraph, Map.of(), Map.of("payload", List.of("unsafe"))))
                .isInstanceOf(IllegalArgumentException.class);

        BatchLogContext oversizedString = batchEventLogger.start("content-sync");
        assertThatThrownBy(() -> batchEventLogger.succeeded(
                        oversizedString, Map.of(), Map.of("summary", "x".repeat(501))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<JsonNode> events() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .peek(message -> {
                    assertThat(message).startsWith(PREFIX + "{");
                    assertThat(message.indexOf(PREFIX)).isZero();
                    assertThat(message.lastIndexOf(PREFIX)).isZero();
                })
                .map(message -> {
                    try {
                        return objectMapper.readTree(message.substring(PREFIX.length()));
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                })
                .toList();
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }

    private static TaskRunTerminalSnapshot snapshot(
            TaskRunStatus status, String errorType, String errorMessage) {
        return new TaskRunTerminalSnapshot(
                UUID.randomUUID(),
                TaskType.CONTENT_SYNC,
                TriggerType.SCHEDULED,
                status,
                Instant.parse("2026-08-22T02:59:58Z"),
                Instant.parse("2026-08-22T03:00:00Z"),
                null,
                4,
                2,
                1,
                1,
                errorType,
                errorMessage);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
