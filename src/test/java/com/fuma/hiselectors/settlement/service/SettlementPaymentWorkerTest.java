package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementPaymentWorkerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void holdsForMissingAccountInformation() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistory history = pendingHistory();
        Selectors selectors = mock(Selectors.class);
        when(historyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(selectorsRepository.findById(9L)).thenReturn(Optional.of(selectors));
        when(selectors.getSelectorsRoleId()).thenReturn("ACTIVE");
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.empty());

        var outcome = new SettlementPaymentWorker(
                historyRepository, selectorsRepository, accountRepository, CLOCK).process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_INFO);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_INFO);
    }

    @Test
    void blacklistTakesPriorityOverACompleteAccount() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistory history = pendingHistory();
        Selectors selectors = mock(Selectors.class);
        SettlementAccount account = SettlementAccount.builder().selectorsId(9L)
                .bankName("국민은행").accountNumber("123").accountHolder("홍길동").build();
        when(historyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(selectorsRepository.findById(9L)).thenReturn(Optional.of(selectors));
        when(selectors.getSelectorsRoleId()).thenReturn("BLACKLIST");
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));

        var outcome = new SettlementPaymentWorker(
                historyRepository, selectorsRepository, accountRepository, CLOCK).process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_BLACK);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_BLACK);
    }

    private SettlementHistory pendingHistory() {
        SettlementHistory history = SettlementHistory.create(9L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        return history;
    }
}
