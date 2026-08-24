package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementAccountServiceTest {

    @Test
    void inactiveSelectorCanGetAccountWithSettlementGuard() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = SettlementAccount.builder()
                .selectorsId(9L)
                .bankName("국민은행")
                .accountNumber("123-456")
                .accountHolder("홍길동")
                .build();
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService);

        when(selectorAccessService.requireSettlementReadable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));

        var response = service.getAccount("selector-user");

        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.accountNumber()).isEqualTo("123-456");
        assertThat(response.accountHolder()).isEqualTo("홍길동");
        verify(selectorAccessService).requireSettlementReadable("selector-user");
    }

    @Test
    void inactiveSelectorCanUpsertAccountAndReopenOnlyInformationHold() {
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = inactiveSelectors();
        SettlementAccount account = SettlementAccount.builder().selectorsId(9L).build();
        SettlementHistory infoHold = heldHistory(SettlementStatus.PAYMENT_HOLD_INFO);
        SettlementHistory blackHold = heldHistory(SettlementStatus.PAYMENT_HOLD_BLACK);
        SettlementAccountService service = new SettlementAccountService(
                accountRepository, historyRepository, selectorAccessService);

        when(selectorAccessService.requireSettlementWritable("selector-user"))
                .thenReturn(selectors);
        when(accountRepository.findFirstBySelectorsIdAndDeletedFalseOrderByIdDesc(9L))
                .thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);
        when(historyRepository.findAllBySelectorsIdAndStatus(9L, SettlementStatus.PAYMENT_HOLD_INFO))
                .thenReturn(List.of(infoHold));

        var response = service.upsert("selector-user",
                new SettlementAccountUpsertRequest(" 국민은행 ", " 123-456 ", " 홍길동 "));

        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.accountNumber()).isEqualTo("123-456");
        assertThat(response.accountHolder()).isEqualTo("홍길동");
        assertThat(infoHold.getStatus()).isEqualTo(SettlementStatus.PAYMENT_PENDING);
        assertThat(blackHold.getStatus()).isEqualTo(SettlementStatus.PAYMENT_HOLD_BLACK);
        verify(selectorAccessService).requireSettlementWritable("selector-user");
        verify(accountRepository).save(account);
    }

    private Selectors inactiveSelectors() {
        Selectors selectors = Selectors.builder()
                .selectorsRoleId(Selectors.INACTIVE_ROLE)
                .build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        return selectors;
    }

    private SettlementHistory heldHistory(SettlementStatus holdStatus) {
        SettlementHistory history = SettlementHistory.create(9L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        history.transitionTo(holdStatus, LocalDateTime.of(2026, 7, 20, 0, 0));
        return history;
    }
}
