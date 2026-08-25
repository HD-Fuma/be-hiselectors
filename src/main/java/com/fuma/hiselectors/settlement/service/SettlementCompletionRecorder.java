package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementCompletionRecorder {

    private final SelectorsGenerationRepository selectorsGenerationRepository;

    public void record(SettlementHistory history) {
        YearMonth activityMonth = YearMonth.from(history.getActivityMonth());
        List<SelectorsGeneration> memberships = selectorsGenerationRepository
                .findAllBySelectorsIdAndActivityMonthForUpdate(
                        history.getSelectorsId(),
                        activityMonth.atDay(1).atStartOfDay(),
                        activityMonth.plusMonths(1).atDay(1).atStartOfDay());
        if (memberships.size() != 1) {
            log.warn("기수별 정산 성과 집계 대상이 명확하지 않음: settlementId={}, selectorsId={}, "
                            + "activityMonth={}, membershipCount={}",
                    history.getId(), history.getSelectorsId(), activityMonth, memberships.size());
            return;
        }

        memberships.getFirst().addSettledSettlement(
                history.getTotalSales(),
                history.getConfirmedPurchaseCount() == null
                        ? 0L : history.getConfirmedPurchaseCount(),
                history.getSettlementAmount());
    }
}
