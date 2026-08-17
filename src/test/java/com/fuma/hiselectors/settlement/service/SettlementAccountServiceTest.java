package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.model.SettlementAccount;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementAccountServiceTest {

    @Test
    void upsertReopensOnlyInformationHold() {
        UserRepository userRepository = mock(UserRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAccountRepository accountRepository = mock(SettlementAccountRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        User user = mock(User.class);
        Selectors selectors = mock(Selectors.class);
        SettlementAccount account = SettlementAccount.builder().selectorsId(9L).build();
        SettlementHistory infoHold = heldHistory(SettlementStatus.PAYMENT_HOLD_INFO);
        SettlementHistory blackHold = heldHistory(SettlementStatus.PAYMENT_HOLD_BLACK);
        SettlementAccountService service = new SettlementAccountService(
                userRepository, selectorsRepository, accountRepository, historyRepository);

        when(user.getId()).thenReturn(3L);
        when(selectors.getId()).thenReturn(9L);
        when(userRepository.findByHiId("selector-user")).thenReturn(Optional.of(user));
        when(selectorsRepository.findByUserId(3L)).thenReturn(Optional.of(selectors));
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
        verify(accountRepository).save(account);
    }

    private SettlementHistory heldHistory(SettlementStatus holdStatus) {
        SettlementHistory history = SettlementHistory.create(9L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        history.transitionTo(holdStatus, LocalDateTime.of(2026, 7, 20, 0, 0));
        return history;
    }
}
