package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 지급 대상 한 건을 독립 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
public class SettlementPaymentWorker {

    private static final String BLACKLIST_ROLE = "BLACKLIST";

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SelectorsRepository selectorsRepository;
    private final SettlementAccountRepository settlementAccountRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentOutcome process(Long settlementId) {
        SettlementHistory history = settlementHistoryRepository.findByIdForUpdate(settlementId)
                .orElse(null);
        if (history == null || history.getStatus() != SettlementStatus.PAYMENT_PENDING) {
            return PaymentOutcome.SKIPPED;
        }

        Selectors selectors = selectorsRepository.findById(history.getSelectorsId()).orElse(null);
        SettlementAccount account = selectors == null ? null
                : settlementAccountRepository
                        .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(
                                history.getSelectorsId())
                        .orElse(null);
        LocalDateTime processedAt = LocalDateTime.now(clock);

        if (isPaymentHold(selectors, account)) {
            history.transitionTo(SettlementStatus.PAYMENT_HOLD, processedAt);
            return PaymentOutcome.HELD;
        }

        history.transitionTo(SettlementStatus.SETTLED, processedAt);
        return PaymentOutcome.SETTLED;
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

    public enum PaymentOutcome {
        SETTLED,
        HELD,
        SKIPPED
    }
}
