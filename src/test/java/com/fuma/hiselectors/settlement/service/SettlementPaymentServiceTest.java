package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementSourceCode;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementPaymentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void settlesValidAccountAndHoldsBlacklistOrMissingAccount() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, selectorsRepository, accountRepository, clock);

        SettlementHistory validHistory = pendingHistory(1L);
        SettlementHistory blacklistHistory = pendingHistory(2L);
        SettlementHistory missingAccountHistory = pendingHistory(3L);
        when(historyRepository.findAllBySettlementMonthAndStatus(
                LocalDateTime.of(2026, 6, 1, 0, 0), SettlementStatus.PAYMENT_PENDING))
                .thenReturn(List.of(validHistory, blacklistHistory, missingAccountHistory));

        Selectors validSelectors = mock(Selectors.class);
        when(validSelectors.getSelectorsRoleId()).thenReturn("ACTIVE");
        Selectors blacklistSelectors = mock(Selectors.class);
        when(blacklistSelectors.getSelectorsRoleId()).thenReturn("BLACKLIST");
        Selectors missingAccountSelectors = mock(Selectors.class);
        when(missingAccountSelectors.getSelectorsRoleId()).thenReturn("ACTIVE");
        when(selectorsRepository.findById(1L)).thenReturn(Optional.of(validSelectors));
        when(selectorsRepository.findById(2L)).thenReturn(Optional.of(blacklistSelectors));
        when(selectorsRepository.findById(3L)).thenReturn(Optional.of(missingAccountSelectors));

        SettlementAccount account = mock(SettlementAccount.class);
        when(account.getBankName()).thenReturn("국민은행");
        when(account.getAccountNumber()).thenReturn("123-456");
        when(account.getAccountHolder()).thenReturn("홍길동");
        when(account.getBusinessNumber()).thenReturn("123-45-67890");
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(1L))
                .thenReturn(Optional.of(account));
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(3L))
                .thenReturn(Optional.empty());

        var result = service.process(YearMonth.of(2026, 6));

        assertThat(result.targetSettlementMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(result.settledCount()).isEqualTo(1);
        assertThat(result.heldCount()).isEqualTo(2);
        assertThat(validHistory.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(blacklistHistory.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD);
        assertThat(missingAccountHistory.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD);
    }

    private SettlementHistory pendingHistory(Long selectorsId) {
        SettlementHistory history = SettlementHistory.create(
                selectorsId, LocalDateTime.of(2026, 6, 1, 0, 0));
        history.updateCalculation(
                10_000L, 1L, new BigDecimal("3.00"), 300L,
                SettlementSourceCode.DAILY_BATCH, LocalDateTime.of(2026, 7, 1, 3, 0));
        history.transitionTo(
                SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 7, 22, 0, 0));
        return history;
    }
}
