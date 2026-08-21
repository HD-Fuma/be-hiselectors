package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.purchase.repository.PurchaseProvisionalSettlementSummary;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementProvisionalEstimateServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void calculatesCurrentMonthFromPurchasedAndConfirmedPurchases() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), SEOUL);
        SettlementProvisionalEstimateService service =
                new SettlementProvisionalEstimateService(repository, clock);
        SettlementHistory history = SettlementHistory.create(
                9L, LocalDateTime.of(2026, 8, 1, 0, 0));
        history.updateCalculation(
                100_000L, 1L, new BigDecimal("5.00"), 5_000L,
                LocalDateTime.of(2026, 8, 21, 3, 0));
        PurchaseProvisionalSettlementSummary summary =
                mock(PurchaseProvisionalSettlementSummary.class);
        when(summary.getTotalSales()).thenReturn(new BigDecimal("124800.00"));
        when(summary.getPurchaseCount()).thenReturn(2L);
        when(repository.summarizeProvisionalPurchasesForActivityMonth(
                9L,
                List.of(PurchaseStatus.PURCHASED, PurchaseStatus.PURCHASE_CONFIRMED),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0))).thenReturn(summary);

        var result = service.calculate(history);

        assertThat(result.purchaseCount()).isEqualTo(2L);
        assertThat(result.salesAmount()).isEqualTo(124_800L);
        assertThat(result.settlementAmount()).isEqualTo(6_240L);
        assertThat(result.asOf()).isEqualTo(LocalDateTime.of(2026, 8, 21, 12, 0));
        verify(repository).summarizeProvisionalPurchasesForActivityMonth(
                9L,
                List.of(PurchaseStatus.PURCHASED, PurchaseStatus.PURCHASE_CONFIRMED),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    @Test
    void omitsProvisionalEstimateForPastActivityMonth() {
        PurchaseHistoryRepository repository = mock(PurchaseHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), SEOUL);
        SettlementProvisionalEstimateService service =
                new SettlementProvisionalEstimateService(repository, clock);
        SettlementHistory history = SettlementHistory.create(
                9L, LocalDateTime.of(2026, 7, 1, 0, 0));
        history.updateCalculation(
                100_000L, 1L, new BigDecimal("5.00"), 5_000L,
                LocalDateTime.of(2026, 8, 21, 3, 0));

        assertThat(service.calculate(history)).isNull();
        verifyNoInteractions(repository);
    }
}
