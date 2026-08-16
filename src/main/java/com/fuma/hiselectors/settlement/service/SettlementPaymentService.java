package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementPaymentService {

    private static final String BLACKLIST_ROLE = "BLACKLIST";

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SelectorsRepository selectorsRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final Clock clock;

    /** 매월 지급일에 실행할 전전월 정산 지급 처리. */
    @Transactional
    public SettlementPaymentResponse processPreviousPreviousMonth() {
        YearMonth targetMonth = YearMonth.from(LocalDate.now(clock)).minusMonths(2);
        return process(targetMonth);
    }

    /** 관리자 수동 실행 및 스케줄러가 공유하는 지급 상태 처리. */
    @Transactional
    public SettlementPaymentResponse process(YearMonth targetMonth) {
        LocalDateTime monthStart = targetMonth.atDay(1).atStartOfDay();
        List<SettlementHistory> histories = settlementHistoryRepository
                .findAllBySettlementMonthAndStatus(monthStart, SettlementStatus.PAYMENT_PENDING);
        LocalDateTime processedAt = LocalDateTime.now(clock);
        int settledCount = 0;
        int heldCount = 0;

        for (SettlementHistory history : histories) {
            Selectors selectors = selectorsRepository.findById(history.getSelectorsId()).orElse(null);
            SettlementAccount account = selectors == null ? null
                    : settlementAccountRepository
                            .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(
                                    history.getSelectorsId())
                            .orElse(null);

            if (isPaymentHold(selectors, account)) {
                history.transitionTo(SettlementStatus.PAYMENT_HOLD, processedAt);
                heldCount++;
            } else {
                history.transitionTo(SettlementStatus.SETTLED, processedAt);
                settledCount++;
            }
        }

        int processedCount = histories.size();
        log.info(
                "정산 지급 상태 처리 완료: targetSettlementMonth={}, processed={}, settled={}, held={}",
                targetMonth, processedCount, settledCount, heldCount);
        return new SettlementPaymentResponse(
                targetMonth, processedCount, settledCount, heldCount, 0);
    }

    private boolean isPaymentHold(Selectors selectors, SettlementAccount account) {
        return selectors == null
                || BLACKLIST_ROLE.equalsIgnoreCase(selectors.getSelectorsRoleId())
                || isSettlementAccountMissing(account);
    }

    private boolean isSettlementAccountMissing(SettlementAccount account) {
        return account == null
                || isBlank(account.getBankName())
                || isBlank(account.getAccountNumber())
                || isBlank(account.getAccountHolder())
                || isBlank(account.getBusinessNumber());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
