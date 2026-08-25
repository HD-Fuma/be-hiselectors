package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskRunProgressEventTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void carriesAnAbsoluteStepSnapshot() {
        TaskRunProgressEvent event = new TaskRunProgressEvent(RUN_ID, "youtube", 10L, 4L);

        assertThat(event.runId()).isEqualTo(RUN_ID);
        assertThat(event.stepKey()).isEqualTo("youtube");
        assertThat(event.totalCount()).isEqualTo(10L);
        assertThat(event.processedCount()).isEqualTo(4L);
    }

    @Test
    void allowsAnUnknownTotal() {
        assertThat(new TaskRunProgressEvent(RUN_ID, "youtube", null, 4L).totalCount()).isNull();
    }

    @Test
    void requiresRunId() {
        assertThatThrownBy(() -> new TaskRunProgressEvent(null, "youtube", 10L, 4L))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("실행 ID는 필수입니다.");
    }

    @Test
    void requiresStepKey() {
        assertThatThrownBy(() -> new TaskRunProgressEvent(RUN_ID, null, 10L, 4L))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("단계 키는 필수입니다.");
        assertThatThrownBy(() -> new TaskRunProgressEvent(RUN_ID, " ", 10L, 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 키는 필수입니다.");
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new TaskRunProgressEvent(RUN_ID, "youtube", -1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("전체 건수는 음수일 수 없습니다.");
        assertThatThrownBy(() -> new TaskRunProgressEvent(RUN_ID, "youtube", null, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("처리 건수는 음수일 수 없습니다.");
    }

    @Test
    void rejectsProcessedCountBeyondKnownTotal() {
        assertThatThrownBy(() -> new TaskRunProgressEvent(RUN_ID, "youtube", 3L, 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("처리 건수는 전체 건수를 초과할 수 없습니다.");
    }
}
