package com.fuma.hiselectors.taskrun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TaskStepProgressTest {

    @Test
    void acceptsKnownAndUnknownTotals() {
        assertThat(new TaskStepProgress(10L, 4L))
                .isEqualTo(new TaskStepProgress(10L, 4L));
        assertThat(new TaskStepProgress(null, 4L).totalCount()).isNull();
    }

    @Test
    void rejectsInvalidCountsWithKoreanMessages() {
        assertThatThrownBy(() -> new TaskStepProgress(-1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 전체 건수는 음수일 수 없습니다.");
        assertThatThrownBy(() -> new TaskStepProgress(null, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 처리 건수는 음수일 수 없습니다.");
        assertThatThrownBy(() -> new TaskStepProgress(3L, 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 처리 건수는 전체 건수를 초과할 수 없습니다.");
    }
}
