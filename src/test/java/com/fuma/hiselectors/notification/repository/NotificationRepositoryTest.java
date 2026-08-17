package com.fuma.hiselectors.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.notification.model.Notification;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("알림 목적 코드와 발송 상태를 저장하고 조회한다")
    void saveNotificationWithPurposeCode() {
        Notification notification = notificationRepository.save(Notification.builder()
                .notificationPurposeCode("SELECTION_APPROVED")
                .referenceId(1L)
                .notificationChannel(NotificationChannel.KAKAO_MESSAGE)
                .receiver("recipient-uuid")
                .body("선정되었습니다.")
                .requestAt(LocalDateTime.now())
                .build());
        em.flush();
        em.clear();

        Notification found = notificationRepository.findById(notification.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(NotificationStatus.REQUESTED);
        assertThat(found.getNotificationPurposeCode()).isEqualTo("SELECTION_APPROVED");
    }
}
