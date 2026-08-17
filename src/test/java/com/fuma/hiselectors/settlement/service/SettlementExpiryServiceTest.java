package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementExpiryServiceTest {

    @Test
    void expiresBothKindsOfUnresolvedHoldAfterTwelveMonths() {
        SettlementHistoryRepository repository = mock(SettlementHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2027-07-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementHistory infoHold = heldHistory(SettlementStatus.PAYMENT_HOLD_INFO);
        SettlementHistory blackHold = heldHistory(SettlementStatus.PAYMENT_HOLD_BLACK);
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 1, 9, 0);
        when(repository.findAllByStatusInAndUpdatedAtLessThanEqual(
                eq(EnumSet.of(SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK)),
                eq(cutoff))).thenReturn(List.of(infoHold, blackHold));

        int expired = new SettlementExpiryService(repository, clock).expireLongTermHolds();

        assertThat(expired).isEqualTo(2);
        assertThat(infoHold.getStatus()).isEqualTo(SettlementStatus.EXPIRED);
        assertThat(blackHold.getStatus()).isEqualTo(SettlementStatus.EXPIRED);
        verify(repository).findAllByStatusInAndUpdatedAtLessThanEqual(
                EnumSet.of(SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK), cutoff);
    }

    private SettlementHistory heldHistory(SettlementStatus holdStatus) {
        SettlementHistory history = SettlementHistory.create(1L, LocalDateTime.of(2026, 5, 1, 0, 0));
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 6, 21, 0, 0));
        history.transitionTo(holdStatus, LocalDateTime.of(2026, 7, 20, 0, 0));
        return history;
    }
}
