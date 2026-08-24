package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementStatusNotificationService {

    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("yyyy년 M월");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SelectorsRepository selectorsRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Value("${settlement.notification.sender-admin-login-id:}")
    private String senderAdminLoginId;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUpcoming(Long settlementId, LocalDate paymentDate) {
        if (senderAdminLoginId == null || senderAdminLoginId.isBlank()) {
            return;
        }
        try {
            SettlementHistory history = settlementHistoryRepository.findByIdForUpdate(settlementId)
                    .orElse(null);
            if (history == null || history.getStatus() != SettlementStatus.PAYMENT_PENDING
                    || history.getSettlementAmount() <= 0
                    || alreadySent(NotificationType.SETTLEMENT_UPCOMING, settlementId)) {
                return;
            }
            String detail = MONTH_FORMAT.format(history.getActivityMonth())
                    + " 정산금 "
                    + String.format(Locale.KOREA, "%,d", history.getSettlementAmount())
                    + "원이 " + DATE_FORMAT.format(paymentDate) + "에 정산될 예정이에요.";
            send(history, NotificationType.SETTLEMENT_UPCOMING, detail);
        } catch (RuntimeException exception) {
            log.warn("정산 예정 알림 처리 실패: settlementId={}", settlementId, exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyCompleted(Long settlementId) {
        if (senderAdminLoginId == null || senderAdminLoginId.isBlank()) {
            return;
        }
        try {
            SettlementHistory history = settlementHistoryRepository.findByIdForUpdate(settlementId)
                    .orElse(null);
            if (history == null || history.getStatus() != SettlementStatus.SETTLED
                    || history.getSettlementAmount() <= 0
                    || alreadySent(NotificationType.SETTLEMENT_COMPLETED, settlementId)) {
                return;
            }
            String detail = MONTH_FORMAT.format(history.getActivityMonth())
                    + " 정산금 "
                    + String.format(Locale.KOREA, "%,d", history.getSettlementAmount())
                    + "원의 정산 처리가 완료되었어요.";
            send(history, NotificationType.SETTLEMENT_COMPLETED, detail);
        } catch (RuntimeException exception) {
            log.warn("정산 완료 알림 처리 실패: settlementId={}", settlementId, exception);
        }
    }

    private boolean alreadySent(NotificationType type, Long settlementId) {
        return notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                type.getPurposeCode(), settlementId) > 0;
    }

    private void send(SettlementHistory history, NotificationType type, String detail) {
        Selectors selectors = selectorsRepository.findById(history.getSelectorsId()).orElse(null);
        if (selectors == null || selectors.getUserId() == null) {
            return;
        }
        notificationService.sendToFriendAsSystem(senderAdminLoginId,
                new NotificationMessageCommand(
                        null,
                        selectors.getUserId(),
                        history.getId(),
                        receiverName(selectors),
                        detail,
                        type));
    }

    private String receiverName(Selectors selectors) {
        return selectors.getSelectorsNickname() == null
                || selectors.getSelectorsNickname().isBlank()
                ? "회원"
                : selectors.getSelectorsNickname();
    }
}
