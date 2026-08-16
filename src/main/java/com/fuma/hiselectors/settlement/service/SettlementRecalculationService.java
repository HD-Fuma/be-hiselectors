package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.dto.SettlementRecalculationResponse;
import com.fuma.hiselectors.settlement.model.SettlementSourceCode;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementRecalculationService {

    private static final int FINALIZATION_START_DAY = 22;

    private final SelectorsRepository selectorsRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SettlementCalculationWorker settlementCalculationWorker;
    private final Clock clock;

    public SettlementRecalculationResponse recalculate(
            YearMonth requestedMonth, Long requestedSelectorsId) {
        return recalculate(requestedMonth, requestedSelectorsId, false);
    }

    public SettlementRecalculationResponse recalculate(
            YearMonth requestedMonth, Long requestedSelectorsId, boolean forcePaymentPendingRecalculation) {
        LocalDate today = LocalDate.now(clock);
        List<YearMonth> settlementMonths = resolveSettlementMonths(requestedMonth, today);
        List<Long> selectorsIds = resolveSelectors(requestedSelectorsId);
        RecalculationCounts counts = new RecalculationCounts();

        for (YearMonth settlementMonth : settlementMonths) {
            boolean finalizeSettlement = shouldFinalize(settlementMonth, today);
            for (Long selectorsId : selectorsIds) {
                try {
                    SettlementCalculationResult result = settlementCalculationWorker.calculate(
                            selectorsId,
                            settlementMonth,
                            SettlementSourceCode.DAILY_BATCH,
                            finalizeSettlement,
                            forcePaymentPendingRecalculation);
                    counts.add(result.outcome());
                } catch (RuntimeException e) {
                    counts.failedCount++;
                    log.error(
                            "관리자 정산 재계산 실패: selectorsId={}, settlementMonth={}",
                            selectorsId,
                            settlementMonth,
                            e);
                }
            }
        }

        YearMonth startMonth = settlementMonths.isEmpty() ? null : settlementMonths.getFirst();
        YearMonth endMonth = settlementMonths.isEmpty() ? null : settlementMonths.getLast();
        return new SettlementRecalculationResponse(
                requestedSelectorsId,
                requestedMonth,
                startMonth,
                endMonth,
                selectorsIds.size(),
                settlementMonths.size(),
                counts.createdCount,
                counts.updatedCount,
                counts.finalizedCount,
                counts.skippedCount,
                counts.failedCount);
    }

    private List<Long> resolveSelectors(Long requestedSelectorsId) {
        if (requestedSelectorsId == null) {
            return selectorsRepository.findAllIds();
        }
        if (!selectorsRepository.existsById(requestedSelectorsId)) {
            throw new BusinessException(ErrorCode.SELECTOR_NOT_FOUND);
        }
        return List.of(requestedSelectorsId);
    }

    private List<YearMonth> resolveSettlementMonths(YearMonth requestedMonth, LocalDate today) {
        YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
        if (requestedMonth != null) {
            if (requestedMonth.isAfter(previousMonth)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return List.of(requestedMonth);
        }

        YearMonth earliestMonth = earliestSettlementMonth();
        if (earliestMonth == null || earliestMonth.isAfter(previousMonth)) {
            return List.of();
        }
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = earliestMonth; !month.isAfter(previousMonth);
             month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    private YearMonth earliestSettlementMonth() {
        LocalDateTime earliestPurchasedAt = purchaseHistoryRepository
                .findEarliestPurchasedAtByStatus(PurchaseStatus.PURCHASE_CONFIRMED);
        LocalDateTime earliestHistoryMonth = settlementHistoryRepository.findEarliestSettlementMonth();
        if (earliestPurchasedAt == null) {
            return toYearMonth(earliestHistoryMonth);
        }
        if (earliestHistoryMonth == null || earliestPurchasedAt.isBefore(earliestHistoryMonth)) {
            return YearMonth.from(earliestPurchasedAt);
        }
        return YearMonth.from(earliestHistoryMonth);
    }

    private YearMonth toYearMonth(LocalDateTime value) {
        return value == null ? null : YearMonth.from(value);
    }

    private boolean shouldFinalize(YearMonth settlementMonth, LocalDate today) {
        LocalDate finalizationDate = settlementMonth.plusMonths(1)
                .atDay(FINALIZATION_START_DAY);
        return !today.isBefore(finalizationDate);
    }

    private static final class RecalculationCounts {

        private int createdCount;
        private int updatedCount;
        private int finalizedCount;
        private int skippedCount;
        private int failedCount;

        private void add(SettlementCalculationOutcome outcome) {
            switch (outcome) {
                case CREATED -> createdCount++;
                case UPDATED -> updatedCount++;
                case FINALIZED -> finalizedCount++;
                case SKIPPED -> skippedCount++;
            }
        }
    }
}
