package com.fuma.hiselectors.notification.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class KakaoMessageSendTaskTest {

    @Test
    void reportsOneSuccessfulResend() throws Exception {
        NotificationService service = mock(NotificationService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        KakaoMessageSendTask factory = new KakaoMessageSendTask(service);

        factory.resend("admin", 35L)
                .execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, service);
        order.verify(progress).start("KAKAO_MESSAGE_RESEND", 1);
        order.verify(service).resendFailed("admin", 35L);
        order.verify(progress).advance(1, 0, 0);
    }

    @Test
    void recordsFailureAndRethrowsSoTheRunFails() {
        NotificationService service = mock(NotificationService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        RuntimeException failure = new IllegalStateException("send failed");
        doThrow(failure).when(service).resendFailed("admin", 35L);
        KakaoMessageSendTask factory = new KakaoMessageSendTask(service);
        TrackedTask task = factory.resend("admin", 35L);

        assertThatThrownBy(() -> task.execute(
                new TaskExecutionContext(mock(TaskLease.class), progress)))
                .isSameAs(failure);

        InOrder order = inOrder(progress, service);
        order.verify(progress).start("KAKAO_MESSAGE_RESEND", 1);
        order.verify(service).resendFailed("admin", 35L);
        order.verify(progress).advance(0, 1, 0);
    }

    @Test
    void preservesSendFailureWhenFailureProgressReportingAlsoFails() {
        NotificationService service = mock(NotificationService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        RuntimeException sendFailure = new IllegalStateException("send failed");
        RuntimeException progressFailure = new IllegalStateException("lease lost");
        doThrow(sendFailure).when(service).resendFailed("admin", 35L);
        doThrow(progressFailure).when(progress).advance(0, 1, 0);

        assertThatThrownBy(() -> new KakaoMessageSendTask(service)
                        .resend("admin", 35L)
                        .execute(new TaskExecutionContext(mock(TaskLease.class), progress)))
                .isSameAs(sendFailure);
        assertThat(sendFailure.getSuppressed()).containsExactly(progressFailure);
    }
}
