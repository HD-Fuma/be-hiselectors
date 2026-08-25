package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.purchase.repository.PurchaseSettlementSummary;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.event.SettlementCarryoverConfirmedEvent;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementCalculationWorker {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final SelectorsRepository selectorsRepository;
    private final ApplicationRepository applicationRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final CommissionRateCalculator commissionRateCalculator;
    private final SettlementCompletionRecorder completionRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${settlement.payment.minimum-amount:1000}")
    private long minimumPaymentAmount = 1000L;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementCalculationResult calculate(
            Long selectorsId,
            YearMonth activityMonth,
            boolean finalizeSettlement) {
        return calculate(selectorsId, activityMonth, finalizeSettlement, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementCalculationResult calculate(
            Long selectorsId,
            YearMonth activityMonth,
            boolean finalizeSettlement,
            boolean forcePaymentPendingRecalculation) {
        Selectors selectors = selectorsRepository.findByIdForUpdate(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        LocalDateTime monthStart = activityMonth.atDay(1).atStartOfDay();

        List<SettlementHistory> monthlyHistories = settlementHistoryRepository
                .findAllBySelectorsIdAndActivityMonthGreaterThanEqualAndActivityMonthLessThanOrderByActivityMonthDesc(
                        selectorsId,
                        monthStart,
                        activityMonth.plusMonths(1).atDay(1).atStartOfDay());
        if (monthlyHistories.size() > 1) {
            throw new BusinessException(
                    ErrorCode.SETTLEMENT_ACTIVITY_MONTH_DUPLICATED,
                    "selectorsId=" + selectorsId + ", activityMonth=" + activityMonth);
        }
        SettlementHistory history = monthlyHistories.isEmpty() ? null : monthlyHistories.getFirst();
        boolean preserveExistingPayment = false;
        if (history != null && !history.isCalculating()) {
            if (forcePaymentPendingRecalculation
                    && finalizeSettlement
                    && history.getStatus() == SettlementStatus.PAYMENT_PENDING) {
                preserveExistingPayment = true;
                history.reopenPaymentPendingForRecalculation();
            } else if (forcePaymentPendingRecalculation
                    && finalizeSettlement
                    && history.getStatus() == SettlementStatus.PAYMENT_CARRYOVER) {
                history.reopenCarryoverForRecalculation();
            } else {
                return new SettlementCalculationResult(history, SettlementCalculationOutcome.SKIPPED);
            }
        }

        Application application = requireRateSource(selectors);
        PurchaseSummary summary = summarizePurchases(selectorsId, activityMonth);
        long totalSales = requireWholeWon(summary.totalSales());
        BigDecimal settlementRate = commissionRateCalculator.calculate(
                application.getSnsCode(), application.getFollowerCount());
        long settlementAmount = calculateSettlementAmount(totalSales, settlementRate);
        LocalDateTime now = LocalDateTime.now(clock);

        boolean created = history == null;
        if (created) {
            history = SettlementHistory.create(selectorsId, monthStart);
        }
        history.updateCalculation(
                totalSales,
                summary.confirmedPurchaseCount(),
                settlementRate,
                settlementAmount,
                now);

        SettlementCalculationOutcome outcome = created
                ? SettlementCalculationOutcome.CREATED
                : SettlementCalculationOutcome.UPDATED;
        Long carryoverAccumulatedAmount = null;
        if (finalizeSettlement) {
            if (settlementAmount == 0L) {
                history.transitionTo(SettlementStatus.SETTLED, now);
                completionRecorder.record(history);
            } else if (preserveExistingPayment) {
                history.transitionTo(SettlementStatus.PAYMENT_PENDING, now);
            } else {
                List<SettlementHistory> carryovers = settlementHistoryRepository
                        .findAllBySelectorsIdAndStatusForUpdate(
                                selectorsId, SettlementStatus.PAYMENT_CARRYOVER);
                long accumulatedAmount = accumulatedAmount(carryovers, settlementAmount);
                if (accumulatedAmount < minimumPaymentAmount) {
                    history.transitionTo(SettlementStatus.PAYMENT_CARRYOVER, now);
                    carryoverAccumulatedAmount = accumulatedAmount;
                } else {
                    YearMonth paymentMonth = activityMonth.plusMonths(2);
                    for (SettlementHistory carryover : carryovers) {
                        carryover.transitionTo(SettlementStatus.PAYMENT_PENDING, now);
                        carryover.schedulePayment(paymentMonth);
                    }
                    history.transitionTo(SettlementStatus.PAYMENT_PENDING, now);
                    history.schedulePayment(paymentMonth);
                }
            }
            outcome = SettlementCalculationOutcome.FINALIZED;
        }
        SettlementHistory saved = settlementHistoryRepository.save(history);
        if (carryoverAccumulatedAmount != null) {
            eventPublisher.publishEvent(new SettlementCarryoverConfirmedEvent(
                    saved.getId(), carryoverAccumulatedAmount, minimumPaymentAmount));
        }
        return new SettlementCalculationResult(saved, outcome);
    }

    private long accumulatedAmount(List<SettlementHistory> histories, long currentAmount) {
        try {
            long accumulated = currentAmount;
            for (SettlementHistory history : histories) {
                accumulated = Math.addExact(accumulated, history.getSettlementAmount());
            }
            return accumulated;
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_AMOUNT);
        }
    }

    private Application requireRateSource(Selectors selectors) {
        if (selectors.getApplicationId() == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_RATE_SOURCE_NOT_FOUND);
        }
        return applicationRepository.findById(selectors.getApplicationId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SETTLEMENT_RATE_SOURCE_NOT_FOUND));
    }

    private PurchaseSummary summarizePurchases(Long selectorsId, YearMonth activityMonth) {
        LocalDateTime startInclusive = activityMonth.atDay(1).atStartOfDay();
        LocalDateTime endExclusive = activityMonth.plusMonths(1).atDay(1).atStartOfDay();
        PurchaseSettlementSummary result = purchaseHistoryRepository
                .summarizeConfirmedPurchasesForActivityMonth(
                selectorsId,
                PurchaseStatus.PURCHASE_CONFIRMED,
                startInclusive,
                endExclusive);
        BigDecimal totalSales = result == null || result.getTotalSales() == null
                ? BigDecimal.ZERO
                : result.getTotalSales();
        long count = result == null || result.getConfirmedPurchaseCount() == null
                ? 0L
                : result.getConfirmedPurchaseCount();
        return new PurchaseSummary(totalSales, count);
    }

    private long requireWholeWon(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_AMOUNT);
        }
    }

    private long calculateSettlementAmount(long totalSales, BigDecimal rate) {
        return BigDecimal.valueOf(totalSales)
                .multiply(rate)
                .divide(ONE_HUNDRED, 0, RoundingMode.FLOOR)
                .longValueExact();
    }

    private record PurchaseSummary(BigDecimal totalSales, long confirmedPurchaseCount) {
    }
}
