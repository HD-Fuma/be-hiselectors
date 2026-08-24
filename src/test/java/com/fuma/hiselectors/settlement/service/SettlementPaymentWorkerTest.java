package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementPaymentWorkerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void holdsForMissingAccountInformation() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsGenerationRepository generationRepository = mock(SelectorsGenerationRepository.class);
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
                historyRepository, generationRepository, selectorsRepository,
                accountRepository, CLOCK).process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_INFO);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_INFO);
    }

    @Test
    void blacklistTakesPriorityOverACompleteAccount() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsGenerationRepository generationRepository = mock(SelectorsGenerationRepository.class);
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
                historyRepository, generationRepository, selectorsRepository,
                accountRepository, CLOCK).process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_BLACK);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_BLACK);
    }

    @Test
    void recordsGenerationPerformanceWhenSettlementBecomesSettled() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsGenerationRepository generationRepository = mock(SelectorsGenerationRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistory history = pendingHistory();
        Selectors selectors = mock(Selectors.class);
        SelectorsGeneration membership = SelectorsGeneration.builder()
                .selectorsId(9L).generationId(3L).build();
        SettlementAccount account = SettlementAccount.builder().selectorsId(9L)
                .bankName("국민은행").accountNumber("123").accountHolder("홍길동").build();

        when(historyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(selectorsRepository.findById(9L)).thenReturn(Optional.of(selectors));
        when(selectors.getSelectorsRoleId()).thenReturn("ACTIVE");
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));
        when(generationRepository.findAllBySelectorsIdAndActivityMonthForUpdate(
                9L,
                YearMonth.of(2026, 5).atDay(1).atStartOfDay(),
                YearMonth.of(2026, 6).atDay(1).atStartOfDay()))
                .thenReturn(List.of(membership));

        var outcome = new SettlementPaymentWorker(
                historyRepository, generationRepository, selectorsRepository,
                accountRepository, CLOCK).process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.SETTLED);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(membership.getTotalSales()).isEqualTo(10_000L);
        assertThat(membership.getConfirmedPurchaseCount()).isEqualTo(2L);
        assertThat(membership.getPaidCommissionAmount()).isEqualTo(300L);
    }

    private SettlementHistory pendingHistory() {
        SettlementHistory history = SettlementHistory.create(9L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.updateCalculation(
                10_000L, 2L, new BigDecimal("3.00"), 300L,
                LocalDateTime.of(2026, 6, 1, 3, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        return history;
    }
}
