package com.fuma.hiselectors.performance.notification;

import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceNotificationService {

    private final SelectorsRepository selectorsRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Value("${performance.notification.sender-admin-login-id:}")
    private String senderAdminLoginId;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePurchaseCreated(PurchaseCreatedEvent event) {
        if (senderAdminLoginId == null || senderAdminLoginId.isBlank()) {
            return;
        }
        try {
            Selectors selectors = selectorsRepository.findByIdForUpdate(event.selectorsId())
                    .orElse(null);
            if (selectors == null || selectors.getUserId() == null
                    || alreadySent(selectors.getId())) {
                return;
            }
            notificationService.sendToFriend(senderAdminLoginId,
                    new NotificationMessageCommand(
                            null,
                            selectors.getUserId(),
                            selectors.getId(),
                            receiverName(selectors),
                            null,
                            NotificationType.FIRST_PURCHASE));
        } catch (RuntimeException exception) {
            log.warn("첫 구매 알림 발송 실패: purchaseId={}, selectorsId={}",
                    event.purchaseId(), event.selectorsId(), exception);
        }
    }

    private boolean alreadySent(Long selectorsId) {
        return notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.FIRST_PURCHASE.getPurposeCode(), selectorsId) > 0;
    }

    private String receiverName(Selectors selectors) {
        return selectors.getSelectorsNickname() == null
                || selectors.getSelectorsNickname().isBlank()
                ? "회원"
                : selectors.getSelectorsNickname();
    }
}
