package com.fuma.hiselectors.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.notification.dto.NotificationHistoryResponse;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.notification.model.Notification;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.user.model.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Test
    @DisplayName("카카오 수신 UUID로 연결한 사용자 이름과 Hi ID를 발송 이력에서 검색한다")
    void searchHistoryWithLinkedRecipient() {
        User user = em.persist(User.builder()
                .hiId("hi-selector")
                .name("김하이")
                .build());
        em.persist(UserKakaoRecipient.builder()
                .userId(user.getId())
                .kakaoUserId(101L)
                .kakaoMessageUuid("recipient-uuid")
                .build());
        notificationRepository.save(Notification.builder()
                .notificationPurposeCode("SELECTION_APPROVED")
                .notificationChannel(NotificationChannel.KAKAO_MESSAGE)
                .receiver("recipient-uuid")
                .body("선정 결과를 확인해 주세요.")
                .requestAt(LocalDateTime.of(2026, 8, 15, 9, 0))
                .build());
        em.flush();
        em.clear();

        Page<NotificationHistoryResponse> history = notificationRepository.searchHistory(
                "SELECTION_APPROVED",
                NotificationStatus.REQUESTED,
                LocalDateTime.of(2026, 8, 15, 0, 0),
                LocalDateTime.of(2026, 8, 16, 0, 0),
                "hi-selector",
                null,
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("requestAt"))));

        assertThat(history.getTotalElements()).isEqualTo(1);
        NotificationHistoryResponse result = history.getContent().getFirst();
        assertThat(result.recipientName()).isEqualTo("김하이");
        assertThat(result.recipientHiId()).isEqualTo("hi-selector");
        assertThat(result.receiver()).isEqualTo("recipient-uuid");
    }

    @Test
    @DisplayName("발송 채널로 이력을 걸러 조회한다")
    void searchHistoryByChannel() {
        notificationRepository.save(Notification.builder()
                .notificationPurposeCode("SELECTION_APPROVED")
                .notificationChannel(NotificationChannel.KAKAO_MESSAGE)
                .receiver("kakao-uuid")
                .body("카카오 메시지")
                .requestAt(LocalDateTime.of(2026, 8, 15, 9, 0))
                .build());
        notificationRepository.save(Notification.builder()
                .notificationPurposeCode("SETTLEMENT_MISSING")
                .notificationChannel(NotificationChannel.EMAIL)
                .receiver("creator@example.com")
                .body("이메일")
                .requestAt(LocalDateTime.of(2026, 8, 15, 10, 0))
                .build());
        em.flush();
        em.clear();

        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("requestAt")));
        Page<NotificationHistoryResponse> kakao = notificationRepository.searchHistory(
                null, null, null, null, null, NotificationChannel.KAKAO_MESSAGE, pageable);
        Page<NotificationHistoryResponse> email = notificationRepository.searchHistory(
                null, null, null, null, null, NotificationChannel.EMAIL, pageable);

        assertThat(kakao.getTotalElements()).isEqualTo(1);
        assertThat(kakao.getContent().getFirst().channel()).isEqualTo(NotificationChannel.KAKAO_MESSAGE);
        assertThat(email.getTotalElements()).isEqualTo(1);
        assertThat(email.getContent().getFirst().channel()).isEqualTo(NotificationChannel.EMAIL);
    }
}
