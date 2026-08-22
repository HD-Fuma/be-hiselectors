package com.fuma.hiselectors.performance.notification;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.service.CommissionRateCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
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
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final ApplicationRepository applicationRepository;
    private final CommissionRateCalculator commissionRateCalculator;

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
                    || alreadySent(NotificationType.FIRST_PURCHASE, selectors.getId())) {
                return;
            }
            send(selectors, NotificationType.FIRST_PURCHASE);
        } catch (RuntimeException exception) {
            log.warn("첫 구매 알림 발송 실패: purchaseId={}, selectorsId={}",
                    event.purchaseId(), event.selectorsId(), exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyFirstRevenue(Long selectorsId) {
        if (senderAdminLoginId == null || senderAdminLoginId.isBlank()) {
            return;
        }
        try {
            Selectors selectors = selectorsRepository.findByIdForUpdate(selectorsId).orElse(null);
            if (selectors == null || selectors.getUserId() == null
                    || selectors.getApplicationId() == null
                    || alreadySent(NotificationType.FIRST_REVENUE, selectorsId)) {
                return;
            }
            Application application = applicationRepository.findById(selectors.getApplicationId())
                    .orElse(null);
            if (application == null) {
                return;
            }
            BigDecimal revenue = confirmedRevenue(selectorsId, application);
            if (revenue.signum() <= 0) {
                return;
            }
            send(selectors, NotificationType.FIRST_REVENUE,
                    String.format(Locale.KOREA, "%,d", revenue.longValueExact()));
        } catch (RuntimeException exception) {
            log.warn("첫 수익 알림 발송 실패: selectorsId={}", selectorsId, exception);
        }
    }

    private BigDecimal confirmedRevenue(Long selectorsId, Application application) {
        BigDecimal confirmedSales = purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                selectorsId, PurchaseStatus.PURCHASE_CONFIRMED);
        BigDecimal rate = commissionRateCalculator.calculate(
                application.getSnsCode(), application.getFollowerCount());
        return confirmedSales.multiply(rate)
                .divide(new BigDecimal("100"), 0, RoundingMode.FLOOR);
    }

    private boolean alreadySent(NotificationType type, Long referenceId) {
        return notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                type.getPurposeCode(), referenceId) > 0;
    }

    private void send(Selectors selectors, NotificationType type) {
        send(selectors, type, null);
    }

    private void send(Selectors selectors, NotificationType type, String detail) {
        notificationService.sendToFriend(senderAdminLoginId,
                new NotificationMessageCommand(
                        null,
                        selectors.getUserId(),
                        selectors.getId(),
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
