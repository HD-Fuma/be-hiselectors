package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPaymentService {

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SettlementPaymentWorker settlementPaymentWorker;
    private final Clock clock;

    /** 매월 지급일에 실행할 전전월 정산 지급 처리. */
    public SettlementPaymentResponse processPreviousPreviousMonth() {
        YearMonth targetMonth = YearMonth.from(LocalDate.now(clock)).minusMonths(2);
        return process(targetMonth);
    }

    /** 관리자 수동 실행 및 스케줄러가 공유하는 지급 상태 처리. */
    public SettlementPaymentResponse process(YearMonth targetMonth) {
        LocalDateTime monthStart = targetMonth.atDay(1).atStartOfDay();
        List<SettlementHistory> histories = settlementHistoryRepository
                .findAllBySettlementMonthAndStatus(monthStart, SettlementStatus.PAYMENT_PENDING);
        int settledCount = 0;
        int heldCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (SettlementHistory history : histories) {
            try {
                SettlementPaymentWorker.PaymentOutcome outcome = settlementPaymentWorker
                        .process(history.getId());
                switch (outcome) {
                    case SETTLED -> settledCount++;
                    case HELD -> heldCount++;
                    case SKIPPED -> skippedCount++;
                }
            } catch (RuntimeException e) {
                failedCount++;
                log.error("정산 지급 상태 처리 실패: settlementId={}, targetSettlementMonth={}",
                        history.getId(), targetMonth, e);
            }
        }

        int processedCount = histories.size();
        log.info(
                "정산 지급 상태 처리 완료: targetSettlementMonth={}, processed={}, settled={}, held={}, skipped={}, failed={}",
                targetMonth, processedCount, settledCount, heldCount, skippedCount, failedCount);
        return new SettlementPaymentResponse(
                targetMonth, processedCount, settledCount, heldCount, skippedCount, failedCount);
    }
}
