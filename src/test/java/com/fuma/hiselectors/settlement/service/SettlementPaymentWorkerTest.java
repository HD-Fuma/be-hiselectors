package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.model.SettlementType;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SettlementPaymentWorkerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void holdsForMissingAccountInformation() {
        SettlementHistory history = pendingHistory();

        var outcome = worker(history, null, "ACTIVE").process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_INFO);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_INFO);
    }

    @Test
    void blacklistTakesPriorityOverACompleteAccount() {
        SettlementHistory history = pendingHistory();

        var outcome = worker(history, completeAccount(), "BLACKLIST").process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_BLACK);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_BLACK);
    }

    @ParameterizedTest
    @MethodSource("accountsMissingIdentityInformation")
    void holdsWhenSettlementTypeOrBusinessNumberIsMissing(SettlementAccount account) {
        SettlementHistory history = pendingHistory();

        var outcome = worker(history, account, "ACTIVE").process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_INFO);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_INFO);
    }

    @Test
    void unknownSettlementTypeIsIncomplete() {
        SettlementHistory history = pendingHistory();
        SettlementAccount account = completeAccount("UNKNOWN", "900101-1234567");

        var outcome = worker(history, account, "ACTIVE").process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.HELD_INFO);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_INFO);
    }

    @Test
    void completeActiveAccountSettles() {
        SettlementHistory history = pendingHistory();

        var outcome = worker(history, completeAccount(), "ACTIVE").process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.SETTLED);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.SETTLED);
    }

    @Test
    void incompleteAccountDoesNotReopenInformationHold() {
        SettlementHistory history = heldInformationHistory();
        SettlementAccount incompleteAccount = completeAccount(
                SettlementType.INDIVIDUAL.name(), "invalid-number");

        boolean reopened = worker(history, incompleteAccount, "ACTIVE").reopenIfResolved(1L);

        assertThat(reopened).isFalse();
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_INFO);
    }

    @Test
    void completeAccountReopensInformationHold() {
        SettlementHistory history = heldInformationHistory();

        boolean reopened = worker(history, completeAccount(), "ACTIVE").reopenIfResolved(1L);

        assertThat(reopened).isTrue();
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_PENDING);
    }

    private static Stream<SettlementAccount> accountsMissingIdentityInformation() {
        return Stream.of(
                SettlementAccount.builder().selectorsId(9L)
                        .bankName("국민은행").accountNumber("123").accountHolder("홍길동")
                        .businessNumber("900101-1234567")
                        .build(),
                SettlementAccount.builder().selectorsId(9L)
                        .bankName("국민은행").accountNumber("123").accountHolder("홍길동")
                        .settlementType(SettlementType.INDIVIDUAL.name())
                        .build());
    }

    private SettlementPaymentWorker worker(
            SettlementHistory history, SettlementAccount account, String selectorsRole) {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        Selectors selectors = mock(Selectors.class);
        when(historyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(selectorsRepository.findById(9L)).thenReturn(Optional.of(selectors));
        when(selectors.getSelectorsRoleId()).thenReturn(selectorsRole);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.ofNullable(account));
        return new SettlementPaymentWorker(
                historyRepository, selectorsRepository, accountRepository, CLOCK);
    }

    private SettlementAccount completeAccount() {
        return completeAccount(SettlementType.INDIVIDUAL.name(), "900101-1234567");
    }

    private SettlementAccount completeAccount(String settlementType, String businessNumber) {
        return SettlementAccount.builder().selectorsId(9L)
                .bankName("국민은행").accountNumber("123").accountHolder("홍길동")
                .settlementType(settlementType)
                .businessNumber(businessNumber)
                .build();
    }

    private SettlementHistory pendingHistory() {
        SettlementHistory history = SettlementHistory.create(9L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        return history;
    }

    private SettlementHistory heldInformationHistory() {
        SettlementHistory history = pendingHistory();
        history.transitionTo(
                SettlementStatus.PAYMENT_HOLD_INFO, LocalDateTime.of(2026, 7, 20, 0, 0));
        return history;
    }
}
