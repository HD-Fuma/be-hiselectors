package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.purchase.repository.PurchaseProvisionalSettlementSummary;
import com.fuma.hiselectors.settlement.dto.SettlementProvisionalEstimate;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementProvisionalEstimateService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final List<PurchaseStatus> PROVISIONAL_STATUSES = List.of(
            PurchaseStatus.PURCHASED,
            PurchaseStatus.PURCHASE_CONFIRMED);

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final Clock clock;

    /** 당월 계산 중인 정산에만 실시간 잠정액을 붙인다. */
    public SettlementProvisionalEstimate calculate(SettlementHistory history) {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        if (!history.isCalculating()
                || !YearMonth.from(history.getActivityMonth()).equals(currentMonth)
                || history.getSettlementRate() == null) {
            return null;
        }

        LocalDateTime startInclusive = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime endExclusive = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        PurchaseProvisionalSettlementSummary summary = purchaseHistoryRepository
                .summarizeProvisionalPurchasesForActivityMonth(
                        history.getSelectorsId(),
                        PROVISIONAL_STATUSES,
                        startInclusive,
                        endExclusive);
        BigDecimal totalSales = summary == null || summary.getTotalSales() == null
                ? BigDecimal.ZERO
                : summary.getTotalSales();
        long purchaseCount = summary == null || summary.getPurchaseCount() == null
                ? 0L
                : summary.getPurchaseCount();
        long wholeWonSales = requireWholeWon(totalSales);
        long settlementAmount = BigDecimal.valueOf(wholeWonSales)
                .multiply(history.getSettlementRate())
                .divide(ONE_HUNDRED, 0, RoundingMode.FLOOR)
                .longValueExact();

        return new SettlementProvisionalEstimate(
                purchaseCount,
                wholeWonSales,
                settlementAmount,
                LocalDateTime.now(clock));
    }

    private long requireWholeWon(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_AMOUNT);
        }
    }
}
