package com.fuma.hiselectors.taskrun.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "task-run")
public record TaskRunProperties(Executor executor, Progress progress, Stale stale) {

    @ConstructorBinding
    public TaskRunProperties {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(stale, "stale must not be null");
    }

    public TaskRunProperties(Progress progress, Stale stale) {
        this(new Executor(2, 4, 20, "task-run-"), progress, stale);
    }

    public record Executor(int coreSize, int maxSize, int queueCapacity, String threadPrefix) {

        public Executor {
            if (coreSize <= 0 || maxSize < coreSize || queueCapacity < 0) {
                throw new IllegalArgumentException("executor pool settings are invalid");
            }
            if (threadPrefix == null || threadPrefix.isBlank()) {
                throw new IllegalArgumentException("executor thread prefix must not be blank");
            }
        }
    }

    public record Progress(int flushCount, long flushIntervalMs) {

        public Progress {
            if (flushCount <= 0 || flushIntervalMs <= 0) {
                throw new IllegalArgumentException("progress flush settings must be positive");
            }
        }
    }

    public record Stale(long fixedDelay, Timeouts timeouts) {

        public Stale {
            if (fixedDelay <= 0) {
                throw new IllegalArgumentException("stale fixed delay must be positive");
            }
            Objects.requireNonNull(timeouts, "timeouts must not be null");
        }
    }

    public record Timeouts(
            Duration creatorSync,
            Duration contentSync,
            Duration applicationReportGeneration,
            Duration contentReportGeneration,
            Duration settlementCalculation,
            Duration kakaoMessageSend,
            Duration proposalEmailSend) {

        public Timeouts {
            requirePositive("creatorSync", creatorSync);
            requirePositive("contentSync", contentSync);
            requirePositive("applicationReportGeneration", applicationReportGeneration);
            requirePositive("contentReportGeneration", contentReportGeneration);
            requirePositive("settlementCalculation", settlementCalculation);
            requirePositive("kakaoMessageSend", kakaoMessageSend);
            requirePositive("proposalEmailSend", proposalEmailSend);
        }

        private static void requirePositive(String name, Duration duration) {
            Objects.requireNonNull(duration, name + " must not be null");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }
    }
}
