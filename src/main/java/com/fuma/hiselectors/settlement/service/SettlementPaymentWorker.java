package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 지급 대상 한 건을 독립 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementPaymentWorker {

    private static final String BLACKLIST_ROLE = "BLACKLIST";

    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
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

        Selectors selectors = findSelectors(history.getSelectorsId()).orElse(null);
        SettlementAccount account = findAccount(history.getSelectorsId()).orElse(null);
        LocalDateTime processedAt = LocalDateTime.now(clock);

        if (isBlacklisted(selectors)) {
            history.transitionTo(SettlementStatus.PAYMENT_HOLD_BLACK, processedAt);
            return PaymentOutcome.HELD_BLACK;
        }
        if (isSettlementAccountMissing(account)) {
            history.transitionTo(SettlementStatus.PAYMENT_HOLD_INFO, processedAt);
            return PaymentOutcome.HELD_INFO;
        }

        history.transitionTo(SettlementStatus.SETTLED, processedAt);
        recordSettledSettlement(history);
        return PaymentOutcome.SETTLED;
    }

    private void recordSettledSettlement(SettlementHistory history) {
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reopenIfResolved(Long settlementId) {
        SettlementHistory history = settlementHistoryRepository.findByIdForUpdate(settlementId)
                .orElse(null);
        if (history == null || (history.getStatus() != SettlementStatus.PAYMENT_HOLD_INFO
                && history.getStatus() != SettlementStatus.PAYMENT_HOLD_BLACK)) {
            return false;
        }

        Selectors selectors = findSelectors(history.getSelectorsId()).orElse(null);
        SettlementAccount account = findAccount(history.getSelectorsId()).orElse(null);
        if (isBlacklisted(selectors) || isSettlementAccountMissing(account)) {
            return false;
        }
        history.reopenFromPaymentHold();
        return true;
    }

    private Optional<Selectors> findSelectors(Long selectorsId) {
        return selectorsRepository.findById(selectorsId);
    }

    private Optional<SettlementAccount> findAccount(Long selectorsId) {
        return settlementAccountRepository
                .findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(selectorsId);
    }

    private boolean isBlacklisted(Selectors selectors) {
        return selectors == null || BLACKLIST_ROLE.equalsIgnoreCase(selectors.getSelectorsRoleId());
    }

    private boolean isSettlementAccountMissing(SettlementAccount account) {
        return account == null
                || isBlank(account.getBankName())
                || isBlank(account.getAccountNumber())
                || isBlank(account.getAccountHolder());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum PaymentOutcome {
        SETTLED,
        HELD_INFO,
        HELD_BLACK,
        SKIPPED
    }
}
