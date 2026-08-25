package com.fuma.hiselectors.taskrun.model;

public record TaskStepProgress(Long totalCount, long processedCount) {

    public TaskStepProgress {
        if (totalCount != null && totalCount < 0) {
            throw new IllegalArgumentException("단계 전체 건수는 음수일 수 없습니다.");
        }
        if (processedCount < 0) {
            throw new IllegalArgumentException("단계 처리 건수는 음수일 수 없습니다.");
        }
        if (totalCount != null && processedCount > totalCount) {
            throw new IllegalArgumentException("단계 처리 건수는 전체 건수를 초과할 수 없습니다.");
        }
    }
}
