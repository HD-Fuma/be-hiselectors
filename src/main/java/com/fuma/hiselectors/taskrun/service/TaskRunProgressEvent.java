package com.fuma.hiselectors.taskrun.service;

import java.util.Objects;
import java.util.UUID;

/** CONTENT_SYNC 한 단계의 절대 진행률 스냅샷이다. */
public record TaskRunProgressEvent(
        UUID runId,
        String stepKey,
        Long totalCount,
        long processedCount) {

    public TaskRunProgressEvent {
        Objects.requireNonNull(runId, "실행 ID는 필수입니다.");
        Objects.requireNonNull(stepKey, "단계 키는 필수입니다.");
        if (stepKey.isBlank()) {
            throw new IllegalArgumentException("단계 키는 필수입니다.");
        }
        if (totalCount != null && totalCount < 0) {
            throw new IllegalArgumentException("전체 건수는 음수일 수 없습니다.");
        }
        if (processedCount < 0) {
            throw new IllegalArgumentException("처리 건수는 음수일 수 없습니다.");
        }
        if (totalCount != null && processedCount > totalCount) {
            throw new IllegalArgumentException("처리 건수는 전체 건수를 초과할 수 없습니다.");
        }
    }
}
