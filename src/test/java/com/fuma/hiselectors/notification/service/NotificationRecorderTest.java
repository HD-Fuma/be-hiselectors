package com.fuma.hiselectors.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.notification.model.Notification;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationRecorderTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationRecorder recorder = new NotificationRecorder(repository);

    @Test
    void 인앱_알림은_같은_지원자와_단계에_한번만_완료_상태로_기록한다() {
        recorder.recordInAppOnce("APP_QUANT_START", 1L, "지원자 #1 정량 분석을 시작했습니다.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(notification -> {
            assertThat(notification.getNotificationChannel()).isEqualTo(NotificationChannel.IN_APP);
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getReceiver()).isEqualTo("ADMIN");
        });

        clearInvocations(repository);
        when(repository.countByNotificationPurposeCodeAndReferenceId("APP_QUAL_DONE", 1L))
                .thenReturn(1L);
        recorder.recordInAppOnce("APP_QUAL_DONE", 1L, "중복");

        verify(repository, never()).save(any(Notification.class));
    }
}
