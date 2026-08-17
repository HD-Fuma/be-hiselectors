package com.fuma.hiselectors.notification.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationModelTest {

    @Test
    @DisplayName("알림은 요청 상태로 생성되고 발송 성공 상태로 전환한다")
    void requestedToSent() {
        LocalDateTime requestAt = LocalDateTime.now();
        LocalDateTime sentAt = requestAt.plusSeconds(1);
        Notification notification = notification("발송할 메시지", requestAt);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.REQUESTED);
        assertThat(notification.getRequestAt()).isEqualTo(requestAt);

        notification.markSent(sentAt);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(sentAt);
    }

    @Test
    @DisplayName("알림 발송에 실패하면 실패 상태로 전환한다")
    void requestedToFailed() {
        Notification notification = notification("발송할 메시지", null);

        notification.markFailed();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    @DisplayName("알림 본문은 400자를 초과할 수 없다")
    void rejectTooLongBody() {
        assertThatThrownBy(() -> notification("가".repeat(401), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 본문은 400자를 초과할 수 없습니다.");
    }

    @Test
    @DisplayName("메시지 유형은 DB 알림 목적 코드와 연결된다")
    void notificationTypeMapsPurposeCode() {
        assertThat(NotificationType.values())
                .allSatisfy(type -> assertThat(type.getPurposeCode()).isEqualTo(type.name()));
    }

    private Notification notification(String body, LocalDateTime requestAt) {
        return Notification.builder()
                .notificationPurposeCode("SELECTION_APPROVED")
                .referenceId(1L)
                .notificationChannel(NotificationChannel.KAKAO_MESSAGE)
                .receiver("recipient-uuid")
                .body(body)
                .requestAt(requestAt)
                .build();
    }
}
