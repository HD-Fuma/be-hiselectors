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
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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

    private static final List<SalesMilestone> SALES_MILESTONES = List.of(
            new SalesMilestone(NotificationType.SALES_10M, 10_000_000L),
            new SalesMilestone(NotificationType.SALES_5M, 5_000_000L),
            new SalesMilestone(NotificationType.SALES_1M, 1_000_000L),
            new SalesMilestone(NotificationType.SALES_500K, 500_000L),
            new SalesMilestone(NotificationType.SALES_100K, 100_000L));
    private static final List<OrderMilestone> ORDER_MILESTONES = List.of(
            new OrderMilestone(NotificationType.ORDERS_100, 100L),
            new OrderMilestone(NotificationType.ORDERS_50, 50L),
            new OrderMilestone(NotificationType.ORDERS_10, 10L));

    private final SelectorsRepository selectorsRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final ApplicationRepository applicationRepository;
    private final CommissionRateCalculator commissionRateCalculator;
    private final Clock clock;

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

    /** 구매확정 후 잠금을 한 번만 잡고 관련 성과 알림을 함께 판정한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyConfirmedPerformance(Long selectorsId) {
        if (senderAdminLoginId == null || senderAdminLoginId.isBlank()) {
            return;
        }
        try {
            Selectors selectors = selectorsRepository.findByIdForUpdate(selectorsId).orElse(null);
            if (selectors == null || selectors.getUserId() == null) {
                return;
            }
            BigDecimal confirmedSales = purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                    selectorsId, PurchaseStatus.PURCHASE_CONFIRMED);
            notifyFirstRevenue(selectors, confirmedSales);
            notifySalesMilestone(selectors, confirmedSales);
            notifyOrderMilestone(selectors);
            notifyLastMonthSalesSurpassed(selectors);
        } catch (RuntimeException exception) {
            log.warn("구매확정 성과 알림 처리 실패: selectorsId={}", selectorsId, exception);
        }
    }

    private void notifyFirstRevenue(Selectors selectors, BigDecimal confirmedSales) {
        if (confirmedSales.signum() <= 0 || selectors.getApplicationId() == null
                || alreadySent(NotificationType.FIRST_REVENUE, selectors.getId())) {
            return;
        }
        Application application = applicationRepository.findById(selectors.getApplicationId())
                .orElse(null);
        if (application == null) {
            return;
        }
        BigDecimal revenue = confirmedRevenue(confirmedSales, application);
        if (revenue.signum() > 0) {
            sendSafely(selectors, NotificationType.FIRST_REVENUE,
                    String.format(Locale.KOREA, "%,d", revenue.longValueExact()));
        }
    }

    private void notifySalesMilestone(Selectors selectors, BigDecimal confirmedSales) {
        SalesMilestone milestone = highestReachedMilestone(confirmedSales);
        if (milestone == null || alreadySent(milestone.type(), selectors.getId())) {
            return;
        }
        sendSafely(selectors, milestone.type(),
                String.format(Locale.KOREA, "%,d", milestone.amount()));
    }

    private SalesMilestone highestReachedMilestone(BigDecimal confirmedSales) {
        for (SalesMilestone milestone : SALES_MILESTONES) {
            if (confirmedSales.compareTo(BigDecimal.valueOf(milestone.amount())) >= 0) {
                return milestone;
            }
        }
        return null;
    }

    private void notifyOrderMilestone(Selectors selectors) {
        long confirmedOrders = purchaseHistoryRepository
                .countDistinctOrdersBySelectorsIdAndStatusIn(
                        selectors.getId(), List.of(PurchaseStatus.PURCHASE_CONFIRMED));
        OrderMilestone milestone = highestReachedOrderMilestone(confirmedOrders);
        if (milestone == null || alreadySent(milestone.type(), selectors.getId())) {
            return;
        }
        sendSafely(selectors, milestone.type(), Long.toString(milestone.orders()));
    }

    private void notifyLastMonthSalesSurpassed(Selectors selectors) {
        YearMonth currentMonth = YearMonth.now(clock);
        LocalDateTime currentMonthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime lastMonthStart = currentMonth.minusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        BigDecimal lastMonthSales = purchaseHistoryRepository.sumConfirmedSalesByConfirmedAt(
                selectors.getId(), PurchaseStatus.PURCHASE_CONFIRMED,
                lastMonthStart, currentMonthStart);
        BigDecimal currentMonthSales = purchaseHistoryRepository.sumConfirmedSalesByConfirmedAt(
                selectors.getId(), PurchaseStatus.PURCHASE_CONFIRMED,
                currentMonthStart, nextMonthStart);
        if (currentMonthSales.compareTo(lastMonthSales) <= 0
                || notificationRepository.countByPurposeAndReferenceInPeriod(
                NotificationType.LAST_MONTH_SALES.getPurposeCode(), selectors.getId(),
                currentMonthStart, nextMonthStart) > 0) {
            return;
        }
        sendSafely(selectors, NotificationType.LAST_MONTH_SALES, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyWeeklySalesGrowth(Long selectorsId) {
        if (senderAdminLoginId == null || senderAdminLoginId.isBlank()) {
            return;
        }
        try {
            Selectors selectors = selectorsRepository.findByIdForUpdate(selectorsId).orElse(null);
            if (selectors == null || selectors.getUserId() == null) {
                return;
            }
            LocalDate currentWeek = LocalDate.now(clock)
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDateTime currentWeekStart = currentWeek.atStartOfDay();
            LocalDateTime lastWeekStart = currentWeek.minusWeeks(1).atStartOfDay();
            LocalDateTime twoWeeksAgoStart = currentWeek.minusWeeks(2).atStartOfDay();
            BigDecimal previousSales = purchaseHistoryRepository.sumConfirmedSalesByConfirmedAt(
                    selectorsId, PurchaseStatus.PURCHASE_CONFIRMED,
                    twoWeeksAgoStart, lastWeekStart);
            BigDecimal lastWeekSales = purchaseHistoryRepository.sumConfirmedSalesByConfirmedAt(
                    selectorsId, PurchaseStatus.PURCHASE_CONFIRMED,
                    lastWeekStart, currentWeekStart);
            if (lastWeekSales.compareTo(previousSales) <= 0
                    || notificationRepository.countByPurposeAndReferenceInPeriod(
                    NotificationType.WEEKLY_SALES_GROWTH.getPurposeCode(), selectorsId,
                    currentWeekStart, currentWeek.plusWeeks(1).atStartOfDay()) > 0) {
                return;
            }
            String increaseRate = previousSales.signum() == 0 ? null
                    : lastWeekSales.subtract(previousSales)
                    .multiply(new BigDecimal("100"))
                    .divide(previousSales, 0, RoundingMode.HALF_UP)
                    .toPlainString();
            sendSafely(selectors, NotificationType.WEEKLY_SALES_GROWTH, increaseRate);
        } catch (RuntimeException exception) {
            log.warn("전주 매출 증가 알림 처리 실패: selectorsId={}", selectorsId, exception);
        }
    }

    private OrderMilestone highestReachedOrderMilestone(long confirmedOrders) {
        for (OrderMilestone milestone : ORDER_MILESTONES) {
            if (confirmedOrders >= milestone.orders()) {
                return milestone;
            }
        }
        return null;
    }

    private BigDecimal confirmedRevenue(BigDecimal confirmedSales, Application application) {
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

    private void sendSafely(Selectors selectors, NotificationType type, String detail) {
        try {
            send(selectors, type, detail);
        } catch (RuntimeException exception) {
            log.warn("성과 알림 발송 실패: selectorsId={}, type={}",
                    selectors.getId(), type, exception);
        }
    }

    private String receiverName(Selectors selectors) {
        return selectors.getSelectorsNickname() == null
                || selectors.getSelectorsNickname().isBlank()
                ? "회원"
                : selectors.getSelectorsNickname();
    }

    private record SalesMilestone(NotificationType type, long amount) {
    }

    private record OrderMilestone(NotificationType type, long orders) {
    }
}
