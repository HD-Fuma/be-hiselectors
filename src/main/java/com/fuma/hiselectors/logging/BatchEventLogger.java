package com.fuma.hiselectors.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class BatchEventLogger {

    private static final Logger log = LoggerFactory.getLogger(BatchEventLogger.class);
    private static final String PREFIX = "BATCH_EVENT ";
    private static final String EVENT = "BATCH_RUN";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_DETAIL_STRING_LENGTH = 500;
    private static final Pattern BATCH_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern METADATA_KEY =
            Pattern.compile("[a-z][a-zA-Z0-9]*(?:-[a-z0-9]+)*");

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BatchEventLogger(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BatchLogContext start(String batch) {
        if (batch == null || !BATCH_NAME.matcher(batch).matches()) {
            throw new IllegalArgumentException("Batch name must be kebab-case");
        }

        Instant startedAt = clock.instant();
        BatchLogContext run = new BatchLogContext(batch, UUID.randomUUID().toString(), startedAt);
        emit(run, Status.STARTED, startedAt, null, null, null, null, null);
        return run;
    }

    public void succeeded(BatchLogContext run, Map<String, Long> counts, Map<String, Object> details) {
        terminal(run, Status.SUCCEEDED, counts, details, null, null);
    }

    public void partialFailure(
            BatchLogContext run, Map<String, Long> counts, Map<String, Object> details) {
        terminal(run, Status.PARTIAL_FAILURE, counts, details, null, null);
    }

    public void skipped(
            BatchLogContext run,
            String reason,
            Map<String, Long> counts,
            Map<String, Object> details) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Skip reason is required");
        }
        terminal(run, Status.SKIPPED, counts, details, reason, null);
    }

    public void failed(BatchLogContext run, Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        EventError error = new EventError(
                throwable.getClass().getSimpleName(), "Batch execution failed");
        terminal(run, Status.FAILED, null, null, null, error);
    }

    private void terminal(
            BatchLogContext run,
            Status status,
            Map<String, Long> counts,
            Map<String, Object> details,
            String reason,
            EventError error) {
        Map<String, Long> safeCounts = validateCounts(counts);
        Map<String, Object> safeDetails = validateDetails(details);
        Objects.requireNonNull(run, "run").markTerminal();
        Instant endedAt = clock.instant();
        emit(
                run,
                status,
                endedAt,
                Duration.between(run.startedAt(), endedAt).toMillis(),
                safeCounts,
                safeDetails,
                reason,
                error);
    }

    private void emit(
            BatchLogContext run,
            Status status,
            Instant timestamp,
            Long durationMs,
            Map<String, Long> counts,
            Map<String, Object> details,
            String reason,
            EventError error) {
        Event event = new Event(
                SCHEMA_VERSION,
                EVENT,
                run.batch(),
                run.runId(),
                status,
                OffsetDateTime.ofInstant(timestamp, clock.getZone()).toString(),
                durationMs,
                counts,
                details,
                reason,
                error);
        try {
            log.info("{}{}", PREFIX, objectMapper.writeValueAsString(event));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize batch event", exception);
        }
    }

    private static Map<String, Long> validateCounts(Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return null;
        }
        counts.forEach((key, value) -> {
            validateMetadataKey(key);
            if (value == null || value < 0) {
                throw new IllegalArgumentException("Count values must be non-negative");
            }
        });
        return Map.copyOf(counts);
    }

    private static Map<String, Object> validateDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        details.forEach((key, value) -> {
            validateMetadataKey(key);
            if (!isSafeDetail(value)) {
                throw new IllegalArgumentException(
                        "Detail values must be bounded strings, numbers, or booleans");
            }
        });
        return Map.copyOf(details);
    }

    private static void validateMetadataKey(String key) {
        if (key == null || key.length() > 64 || !METADATA_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Metadata keys must be safe identifiers");
        }
    }

    private static boolean isSafeDetail(Object value) {
        if (value instanceof String string) {
            return string.length() <= MAX_DETAIL_STRING_LENGTH;
        }
        if (value instanceof Double number) {
            return Double.isFinite(number);
        }
        if (value instanceof Float number) {
            return Float.isFinite(number);
        }
        return value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Event(
            int schemaVersion,
            String event,
            String batch,
            String runId,
            Status status,
            String timestamp,
            Long durationMs,
            Map<String, Long> counts,
            Map<String, Object> details,
            String reason,
            EventError error) {
    }

    private record EventError(String type, String message) {
    }

    private enum Status {
        STARTED,
        SUCCEEDED,
        PARTIAL_FAILURE,
        FAILED,
        SKIPPED
    }
}
