package com.fuma.hiselectors.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseAutoConfirmationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void confirmsPurchasesFromTheSeventhCalendarDate() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-07T15:05:00Z"), SEOUL);
        PurchaseAutoConfirmationService service =
                new PurchaseAutoConfirmationService(repository, clock);
        LocalDateTime cutoffExclusive = LocalDateTime.of(2026, 8, 2, 0, 0);
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 8, 8, 0, 5);
        when(repository.confirmExpiredPurchases(
                PurchaseStatus.PURCHASED,
                PurchaseStatus.PURCHASE_CONFIRMED,
                cutoffExclusive,
                confirmedAt)).thenReturn(3);
        when(repository.findDistinctSelectorsIdsByStatusAndConfirmedAt(
                PurchaseStatus.PURCHASE_CONFIRMED, confirmedAt)).thenReturn(List.of(4L, 7L));

        PurchaseAutoConfirmationService.ConfirmationResult result =
                service.confirmExpiredPurchases();

        assertThat(result.confirmedCount()).isEqualTo(3);
        assertThat(result.selectorsIds()).containsExactly(4L, 7L);
        verify(repository).confirmExpiredPurchases(
                PurchaseStatus.PURCHASED,
                PurchaseStatus.PURCHASE_CONFIRMED,
                cutoffExclusive,
                confirmedAt);
        verify(repository).findDistinctSelectorsIdsByStatusAndConfirmedAt(
                PurchaseStatus.PURCHASE_CONFIRMED, confirmedAt);
    }
}
