package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementEstimateServiceTest {

    @Test
    void rejectsUserRefreshAfterClosingDay() {
        UserRepository userRepository = mock(UserRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-21T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementEstimateService service = new SettlementEstimateService(
                userRepository, selectorsRepository, historyRepository, worker, clock);

        assertThatThrownBy(() -> service.refreshPreviousMonth("selector-user"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SETTLEMENT_PERIOD_CLOSED);
        verifyNoInteractions(userRepository, selectorsRepository, historyRepository, worker);
    }

    @Test
    void getsOnlyTheAuthenticatedSelectorsHistoriesForTheRequestedYear() {
        UserRepository userRepository = mock(UserRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        User user = mock(User.class);
        Selectors selectors = mock(Selectors.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementEstimateService service = new SettlementEstimateService(
                userRepository, selectorsRepository, historyRepository, worker, clock);

        when(user.getId()).thenReturn(3L);
        when(selectors.getId()).thenReturn(9L);
        when(userRepository.findByHiId("selector-user")).thenReturn(java.util.Optional.of(user));
        when(selectorsRepository.findByUserId(3L)).thenReturn(java.util.Optional.of(selectors));
        when(historyRepository
                .findAllBySelectorsIdAndSettlementMonthGreaterThanEqualAndSettlementMonthLessThanOrderBySettlementMonthDesc(
                        9L, LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0)))
                .thenReturn(List.of());
        when(historyRepository.findAvailableYearsBySelectorsId(9L)).thenReturn(List.of(2026, 2025));

        var result = service.getHistories("selector-user", 2025);

        assertThat(result.selectedYear()).isEqualTo(2025);
        assertThat(result.availableYears()).containsExactly(2026, 2025);
        assertThat(result.histories()).isEmpty();
        verify(historyRepository)
                .findAllBySelectorsIdAndSettlementMonthGreaterThanEqualAndSettlementMonthLessThanOrderBySettlementMonthDesc(
                        9L, LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
