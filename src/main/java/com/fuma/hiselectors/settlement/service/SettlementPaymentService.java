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
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPaymentService {

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SettlementPaymentWorker settlementPaymentWorker;
    private final SettlementMissingNotificationService settlementMissingNotificationService;
    private final Clock clock;

    /** 지급월 실행일에 처리 가능한 모든 활동월 정산을 내부 지급 처리한다. */
    public SettlementPaymentResponse processCurrentPaymentMonth() {
        YearMonth paymentMonth = YearMonth.from(LocalDate.now(clock));
        return process(paymentMonth);
    }

    /** 관리자 수동 실행 및 스케줄러가 공유하는 지급 상태 처리. */
    public SettlementPaymentResponse process(YearMonth paymentMonth) {
        YearMonth latestEligibleActivityMonth = paymentMonth.minusMonths(2);
        reopenResolvedHolds();
        List<SettlementHistory> histories = settlementHistoryRepository
                .findAllByStatusAndActivityMonthLessThanEqualOrderByActivityMonthAsc(
                        SettlementStatus.PAYMENT_PENDING,
                        latestEligibleActivityMonth.atDay(1).atStartOfDay());
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
                    case HELD_INFO -> {
                        heldCount++;
                        settlementMissingNotificationService.notifyMissing(
                                history.getId(), history.getSelectorsId());
                    }
                    case HELD_BLACK -> heldCount++;
                    case SKIPPED -> skippedCount++;
                }
            } catch (RuntimeException e) {
                failedCount++;
                log.error("정산 지급 상태 처리 실패: settlementId={}, paymentMonth={}",
                        history.getId(), paymentMonth, e);
            }
        }

        int processedCount = histories.size();
        log.info(
                "정산 지급 상태 처리 완료: paymentMonth={}, latestEligibleActivityMonth={}, processed={}, settled={}, held={}, skipped={}, failed={}",
                paymentMonth, latestEligibleActivityMonth, processedCount, settledCount, heldCount,
                skippedCount, failedCount);
        return new SettlementPaymentResponse(
                paymentMonth, latestEligibleActivityMonth, processedCount, settledCount, heldCount,
                skippedCount, failedCount);
    }

    private void reopenResolvedHolds() {
        settlementHistoryRepository.findAllByStatusIn(EnumSet.of(
                        SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK))
                .forEach(history -> settlementPaymentWorker.reopenIfResolved(history.getId()));
    }
}
