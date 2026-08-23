package com.fuma.hiselectors.taskrun.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task-run")
public record TaskRunProperties(Progress progress, Stale stale) {

    public TaskRunProperties {
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(stale, "stale must not be null");
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
