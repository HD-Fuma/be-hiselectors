package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 지원서 제출이 커밋되면 지원자에게 접수 완료 카카오 알림을 발송하는 최선형(best-effort) 리스너. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationSubmittedNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifySubmitted(ApplicationSubmittedEvent event) {
        try {
            notificationService.sendToFriendAsSystem(new NotificationMessageCommand(
                    null,
                    event.userId(),
                    event.applicationId(),
                    event.name(),
                    null,
                    NotificationType.APPLICATION_SUBMITTED));
        } catch (RuntimeException e) {
            log.warn("지원 접수 알림 발송 실패: applicationId={}", event.applicationId(), e);
        }
    }
}
