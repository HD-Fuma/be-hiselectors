package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementExpiryWorkerTest {

    @Test
    void expiresOnlyTheStillEligibleLockedHistory() {
        SettlementHistoryRepository repository = mock(SettlementHistoryRepository.class);
        SettlementHistory history = mock(SettlementHistory.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime expiredAt = LocalDateTime.of(2027, 7, 1, 0, 0);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(history.getStatus()).thenReturn(SettlementStatus.PAYMENT_HOLD_INFO);
        when(history.getUpdatedAt()).thenReturn(cutoff);

        boolean expired = new SettlementExpiryWorker(repository)
                .expireIfEligible(1L, cutoff, expiredAt);

        assertThat(expired).isTrue();
        verify(history).transitionTo(SettlementStatus.EXPIRED, expiredAt);
    }

    @Test
    void skipsHistoryThatWasReopenedBeforeTheLockWasAcquired() {
        SettlementHistoryRepository repository = mock(SettlementHistoryRepository.class);
        SettlementHistory history = mock(SettlementHistory.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 1, 0, 0);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(history));
        when(history.getStatus()).thenReturn(SettlementStatus.PAYMENT_PENDING);

        boolean expired = new SettlementExpiryWorker(repository)
                .expireIfEligible(1L, cutoff, LocalDateTime.of(2027, 7, 1, 0, 0));

        assertThat(expired).isFalse();
        verify(history, never()).transitionTo(SettlementStatus.EXPIRED,
                LocalDateTime.of(2027, 7, 1, 0, 0));
    }
}
