package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

    private final SelectorsRepository selectorsRepository;
    private final SettlementCalculationWorker calculationWorker;
    private final SettlementSchedulePolicy schedulePolicy;
    private final Clock clock;

    public SettlementBatchResult calculateOpenActivityMonth() {
        LocalDate today = LocalDate.now(clock);
        YearMonth activityMonth = YearMonth.from(today).minusMonths(1);
        return calculate(activityMonth, false);
    }

    public SettlementBatchResult finalizeOpenActivityMonth() {
        LocalDate today = LocalDate.now(clock);
        if (!schedulePolicy.isFinalizationDate(today)) {
            return SettlementBatchResult.notExecuted(YearMonth.from(today).minusMonths(1));
        }
        return calculate(YearMonth.from(today).minusMonths(1), true);
    }

    private SettlementBatchResult calculate(YearMonth activityMonth, boolean finalizeSettlement) {
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        for (Long selectorsId : selectorsRepository.findAllIds()) {
            try {
                SettlementCalculationResult result = calculationWorker.calculate(
                        selectorsId,
                        activityMonth,
                        finalizeSettlement);
                if (result.outcome() == SettlementCalculationOutcome.SKIPPED) {
                    skipped++;
                } else {
                    processed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("셀렉터스 활동월 정산 계산 실패: selectorsId={}, activityMonth={}",
                        selectorsId, activityMonth, e);
            }
        }

        return new SettlementBatchResult(activityMonth, processed, skipped, failed,
                finalizeSettlement);
    }

    public record SettlementBatchResult(
            YearMonth activityMonth,
            int processedCount,
            int skippedCount,
            int failedCount,
        boolean finalized
    ) {
        public static SettlementBatchResult notExecuted(YearMonth activityMonth) {
            return new SettlementBatchResult(activityMonth, 0, 0, 0, false);
        }
    }
}
