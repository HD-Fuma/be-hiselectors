package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementSourceCode;
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

    private static final int FINALIZATION_START_DAY = 22;

    private final SelectorsRepository selectorsRepository;
    private final SettlementCalculationWorker calculationWorker;
    private final Clock clock;

    public SettlementBatchResult calculatePreviousMonth() {
        LocalDate today = LocalDate.now(clock);
        YearMonth settlementMonth = YearMonth.from(today).minusMonths(1);
        boolean finalizeSettlement = today.getDayOfMonth() >= FINALIZATION_START_DAY;
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        for (Long selectorsId : selectorsRepository.findAllIds()) {
            try {
                SettlementCalculationResult result = calculationWorker.calculate(
                        selectorsId,
                        settlementMonth,
                        SettlementSourceCode.DAILY_BATCH,
                        finalizeSettlement);
                if (result.outcome() == SettlementCalculationOutcome.SKIPPED) {
                    skipped++;
                } else {
                    processed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("셀렉터스 정산 계산 실패: selectorsId={}, settlementMonth={}",
                        selectorsId, settlementMonth, e);
            }
        }

        return new SettlementBatchResult(settlementMonth, processed, skipped, failed,
                finalizeSettlement);
    }

    public record SettlementBatchResult(
            YearMonth settlementMonth,
            int processedCount,
            int skippedCount,
            int failedCount,
            boolean finalized
    ) {
    }
}
