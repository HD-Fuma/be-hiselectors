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
import com.fuma.hiselectors.settlement.model.SettlementSourceCode;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementCalculationResult calculate(
            Long selectorsId,
            YearMonth settlementMonth,
            SettlementSourceCode sourceCode,
            boolean finalizeSettlement) {
        return calculate(selectorsId, settlementMonth, sourceCode, finalizeSettlement, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SettlementCalculationResult calculate(
            Long selectorsId,
            YearMonth settlementMonth,
            SettlementSourceCode sourceCode,
            boolean finalizeSettlement,
            boolean forcePaymentPendingRecalculation) {
        Selectors selectors = selectorsRepository.findByIdForUpdate(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        LocalDateTime monthStart = settlementMonth.atDay(1).atStartOfDay();

        SettlementHistory history = settlementHistoryRepository
                .findBySelectorsIdAndSettlementMonth(selectorsId, monthStart)
                .orElse(null);
        if (history != null && !history.isCalculating()) {
            if (forcePaymentPendingRecalculation
                    && finalizeSettlement
                    && history.getStatus() == SettlementStatus.PAYMENT_PENDING) {
                history.reopenPaymentPendingForRecalculation();
            } else {
                return new SettlementCalculationResult(history, SettlementCalculationOutcome.SKIPPED);
            }
        }

        Application application = requireRateSource(selectors);
        PurchaseSummary summary = summarizePurchases(selectorsId, settlementMonth);
        long totalSales = requireWholeWon(summary.totalSales());
        BigDecimal commissionRate = commissionRateCalculator.calculate(
                application.getSnsCode(), application.getFollowerCount());
        long commission = calculateCommission(totalSales, commissionRate);
        LocalDateTime now = LocalDateTime.now(clock);

        boolean created = history == null;
        if (created) {
            history = SettlementHistory.create(selectorsId, monthStart);
        }
        history.updateCalculation(
                totalSales,
                summary.confirmedPurchaseCount(),
                commissionRate,
                commission,
                sourceCode,
                now);

        SettlementCalculationOutcome outcome = created
                ? SettlementCalculationOutcome.CREATED
                : SettlementCalculationOutcome.UPDATED;
        if (finalizeSettlement) {
            history.transitionTo(SettlementStatus.PAYMENT_PENDING, now);
            outcome = SettlementCalculationOutcome.FINALIZED;
        }
        return new SettlementCalculationResult(settlementHistoryRepository.save(history), outcome);
    }

    private Application requireRateSource(Selectors selectors) {
        if (selectors.getApplicationId() == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_RATE_SOURCE_NOT_FOUND);
        }
        return applicationRepository.findById(selectors.getApplicationId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SETTLEMENT_RATE_SOURCE_NOT_FOUND));
    }

    private PurchaseSummary summarizePurchases(Long selectorsId, YearMonth settlementMonth) {
        LocalDateTime startInclusive = settlementMonth.atDay(1).atStartOfDay();
        LocalDateTime endExclusive = settlementMonth.plusMonths(1).atDay(1).atStartOfDay();
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

    private long calculateCommission(long totalSales, BigDecimal rate) {
        return BigDecimal.valueOf(totalSales)
                .multiply(rate)
                .divide(ONE_HUNDRED, 0, RoundingMode.FLOOR)
                .longValueExact();
    }

    private record PurchaseSummary(BigDecimal totalSales, long confirmedPurchaseCount) {
    }
}
