package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.model.SettlementType;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.settlement.security.SettlementAccountCrypto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SettlementPaymentWorkerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final SettlementAccountCrypto ACCOUNT_CRYPTO = new SettlementAccountCrypto(
            Base64.getEncoder().encodeToString(new byte[32]));

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
    void recordsGenerationPerformanceWhenSettlementBecomesSettled() {
        SettlementHistory history = pendingHistory();
        SelectorsGeneration membership = SelectorsGeneration.builder()
                .selectorsId(9L).generationId(3L).build();

        var outcome = worker(
                history, completeAccount(), "ACTIVE", List.of(membership)).process(1L);

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.SETTLED);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(membership.getTotalSales()).isEqualTo(10_000L);
        assertThat(membership.getConfirmedPurchaseCount()).isEqualTo(2L);
        assertThat(membership.getPaidCommissionAmount()).isEqualTo(300L);
    }

    @Test
    void settlesScheduledPaymentGroupAtomically() {
        SettlementHistory first = pendingHistory(9L, YearMonth.of(2026, 5), 400L);
        SettlementHistory second = pendingHistory(9L, YearMonth.of(2026, 6), 600L);
        first.schedulePayment(YearMonth.of(2026, 8));
        second.schedulePayment(YearMonth.of(2026, 8));
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementCompletionRecorder recorder = mock(SettlementCompletionRecorder.class);
        Selectors selectors = mock(Selectors.class);
        when(historyRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(first, second));
        when(selectorsRepository.findById(9L)).thenReturn(Optional.of(selectors));
        when(selectors.getSelectorsRoleId()).thenReturn("ACTIVE");
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(completeAccount()));
        SettlementPaymentWorker worker = new SettlementPaymentWorker(
                historyRepository, selectorsRepository, accountRepository,
                CLOCK, ACCOUNT_CRYPTO, recorder);

        var outcome = worker.processGroup(List.of(1L, 2L));

        assertThat(outcome).isEqualTo(SettlementPaymentWorker.PaymentOutcome.SETTLED);
        assertThat(first.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(second.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        verify(recorder).record(first);
        verify(recorder).record(second);
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
                        .bankName("국민은행").accountNumberEncrypted(encrypt("123"))
                        .accountHolder("홍길동")
                        .businessNumberEncrypted(encrypt("900101-1234567"))
                        .build(),
                SettlementAccount.builder().selectorsId(9L)
                        .bankName("국민은행").accountNumberEncrypted(encrypt("123"))
                        .accountHolder("홍길동")
                        .settlementType(SettlementType.INDIVIDUAL.name())
                        .build());
    }

    private SettlementPaymentWorker worker(
            SettlementHistory history, SettlementAccount account, String selectorsRole) {
        return worker(history, account, selectorsRole, List.of());
    }

    private SettlementPaymentWorker worker(
            SettlementHistory history, SettlementAccount account, String selectorsRole,
            List<SelectorsGeneration> memberships) {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsGenerationRepository generationRepository = mock(SelectorsGenerationRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        Selectors selectors = mock(Selectors.class);
        when(historyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(selectorsRepository.findById(9L)).thenReturn(Optional.of(selectors));
        when(selectors.getSelectorsRoleId()).thenReturn(selectorsRole);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.ofNullable(account));
        when(generationRepository.findAllBySelectorsIdAndActivityMonthForUpdate(
                9L,
                YearMonth.of(2026, 5).atDay(1).atStartOfDay(),
                YearMonth.of(2026, 6).atDay(1).atStartOfDay()))
                .thenReturn(memberships);
        SettlementCompletionRecorder completionRecorder =
                new SettlementCompletionRecorder(generationRepository);
        return new SettlementPaymentWorker(
                historyRepository, selectorsRepository, accountRepository,
                CLOCK, ACCOUNT_CRYPTO, completionRecorder);
    }

    private SettlementAccount completeAccount() {
        return completeAccount(SettlementType.INDIVIDUAL.name(), "900101-1234567");
    }

    private SettlementAccount completeAccount(String settlementType, String businessNumber) {
        return SettlementAccount.builder().selectorsId(9L)
                .bankName("국민은행").accountNumberEncrypted(encrypt("123"))
                .accountHolder("홍길동")
                .settlementType(settlementType)
                .businessNumberEncrypted(encrypt(businessNumber))
                .build();
    }

    private static String encrypt(String value) {
        return ACCOUNT_CRYPTO.encrypt(value);
    }

    private SettlementHistory pendingHistory() {
        SettlementHistory history = SettlementHistory.create(9L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.updateCalculation(
                10_000L, 2L, new BigDecimal("3.00"), 300L,
                LocalDateTime.of(2026, 6, 1, 3, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        return history;
    }

    private SettlementHistory pendingHistory(
            Long selectorsId, YearMonth activityMonth, long settlementAmount) {
        SettlementHistory history = SettlementHistory.create(
                selectorsId, activityMonth.atDay(1).atStartOfDay());
        history.updateCalculation(
                10_000L, 2L, new BigDecimal("3.00"), settlementAmount,
                activityMonth.plusMonths(1).atDay(1).atStartOfDay());
        history.transitionTo(SettlementStatus.PAYMENT_PENDING,
                activityMonth.plusMonths(1).atDay(21).atStartOfDay());
        return history;
    }

    private SettlementHistory heldInformationHistory() {
        SettlementHistory history = pendingHistory();
        history.transitionTo(
                SettlementStatus.PAYMENT_HOLD_INFO, LocalDateTime.of(2026, 7, 20, 0, 0));
        return history;
    }
}
