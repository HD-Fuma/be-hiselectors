package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementBatchService {

    private final SelectorsRepository selectorsRepository;
    private final SettlementCalculationWorker calculationWorker;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SettlementSchedulePolicy schedulePolicy;
    private final Clock clock;

    public SettlementBatchResult calculateOpenActivityMonth(TaskProgressReporter progress) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentActivityMonth = YearMonth.from(today);
        Set<SettlementTarget> targets = new LinkedHashSet<>();
        for (Long selectorsId : selectorsRepository.findAllIds()) {
            targets.add(new SettlementTarget(selectorsId, currentActivityMonth.minusMonths(1)));
            targets.add(new SettlementTarget(selectorsId, currentActivityMonth));
        }
        progress.start("ESTIMATE", targets.size());
        return calculate(targets, currentActivityMonth, false, progress);
    }

    public SettlementBatchResult finalizeOpenActivityMonth(TaskProgressReporter progress) {
        LocalDate today = LocalDate.now(clock);
        YearMonth openActivityMonth = YearMonth.from(today).minusMonths(1);
        Set<SettlementTarget> targets = overdueCalculatingTargets(today, openActivityMonth);

        if (!today.isBefore(schedulePolicy.finalizationDate(openActivityMonth))) {
            targets.addAll(allSelectorsFor(openActivityMonth));
        }
        progress.start("FINALIZE", targets.size());
        if (targets.isEmpty()) {
            return SettlementBatchResult.notExecuted(openActivityMonth);
        }
        return calculate(targets, openActivityMonth, true, progress);
    }

    private Set<SettlementTarget> allSelectorsFor(YearMonth activityMonth) {
        Set<SettlementTarget> targets = new LinkedHashSet<>();
        for (Long selectorsId : selectorsRepository.findAllIds()) {
            targets.add(new SettlementTarget(selectorsId, activityMonth));
        }
        return targets;
    }

    private Set<SettlementTarget> overdueCalculatingTargets(
            LocalDate today, YearMonth latestActivityMonth) {
        Set<SettlementTarget> targets = new LinkedHashSet<>();
        for (SettlementHistory history : settlementHistoryRepository
                .findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
                        SettlementStatus.CALCULATING, toYearMonthKey(latestActivityMonth))) {
            YearMonth activityMonth = YearMonth.from(history.getActivityMonth());
            if (!today.isBefore(schedulePolicy.finalizationDate(activityMonth))) {
                targets.add(new SettlementTarget(history.getSelectorsId(), activityMonth));
            }
        }
        return targets;
    }

    private int toYearMonthKey(YearMonth yearMonth) {
        return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
    }

    private SettlementBatchResult calculate(
            Set<SettlementTarget> targets,
            YearMonth resultActivityMonth,
            boolean finalizeSettlement,
            TaskProgressReporter progress) {
        int processed = 0;
        int skipped = 0;
        int failed = 0;

        for (SettlementTarget target : targets) {
            SettlementCalculationResult result;
            try {
                result = calculationWorker.calculate(
                        target.selectorsId(),
                        target.activityMonth(),
                        finalizeSettlement);
            } catch (RuntimeException e) {
                failed++;
                log.error("셀렉터스 활동월 정산 계산 실패: selectorsId={}, activityMonth={}",
                        target.selectorsId(), target.activityMonth(), e);
                progress.advance(0, 1, 0);
                continue;
            }
            if (result.outcome() == SettlementCalculationOutcome.SKIPPED) {
                skipped++;
                progress.advance(0, 0, 1);
            } else {
                processed++;
                progress.advance(1, 0, 0);
            }
        }

        return new SettlementBatchResult(resultActivityMonth, processed, skipped, failed,
                finalizeSettlement);
    }

    private record SettlementTarget(Long selectorsId, YearMonth activityMonth) {
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
