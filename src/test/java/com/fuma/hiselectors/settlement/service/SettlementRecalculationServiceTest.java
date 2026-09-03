package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.config.TimeConfig;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementRecalculationServiceTest {

    @Test
    void recalculatesAllSelectorsForEveryHistoricalSettlementMonthByDefault() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PurchaseHistoryRepository purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        SettlementHistoryRepository settlementHistoryRepository = mock(SettlementHistoryRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementPaymentService paymentService = mock(SettlementPaymentService.class);
        SettlementRecalculationService service = service(
                selectorsRepository, purchaseHistoryRepository, settlementHistoryRepository,
                worker, paymentService,
                Instant.parse("2026-08-15T01:00:00Z"));

        when(purchaseHistoryRepository.findEarliestPurchasedAtByStatus(
                PurchaseStatus.PURCHASE_CONFIRMED))
                .thenReturn(LocalDateTime.of(2026, 5, 10, 12, 0));
        when(settlementHistoryRepository.findEarliestActivityMonth())
                .thenReturn(LocalDateTime.of(2026, 6, 1, 0, 0));
        when(selectorsRepository.findAllIds()).thenReturn(List.of(10L, 20L));
        when(worker.calculate(
                anyLong(), any(YearMonth.class), anyBoolean(),
                eq(false)))
                .thenReturn(new SettlementCalculationResult(null, SettlementCalculationOutcome.CREATED));

        var result = service.recalculate(null, null);

        assertThat(result.startActivityMonth()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(result.endActivityMonth()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(result.selectorsCount()).isEqualTo(2);
        assertThat(result.activityMonthsCount()).isEqualTo(3);
        assertThat(result.createdCount()).isEqualTo(6);
        verify(worker).calculate(
                10L, YearMonth.of(2026, 5), true, false);
        verify(worker).calculate(
                20L, YearMonth.of(2026, 7), false, false);
        verify(paymentService).processCurrentPaymentMonth();
    }

    @Test
    void catchesUpOverduePaymentsAfterRecalculation() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PurchaseHistoryRepository purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        SettlementHistoryRepository settlementHistoryRepository = mock(SettlementHistoryRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementPaymentService paymentService = mock(SettlementPaymentService.class);
        SettlementRecalculationService service = service(
                selectorsRepository, purchaseHistoryRepository, settlementHistoryRepository,
                worker, paymentService,
                Instant.parse("2026-09-01T01:00:00Z"));

        when(selectorsRepository.existsById(10L)).thenReturn(true);
        when(worker.calculate(
                10L, YearMonth.of(2026, 6), true, false))
                .thenReturn(new SettlementCalculationResult(null, SettlementCalculationOutcome.SKIPPED));

        var result = service.recalculate(YearMonth.of(2026, 6), 10L);

        assertThat(result.skippedCount()).isEqualTo(1);
        verify(paymentService).processCurrentPaymentMonth();
    }

    @Test
    void keepsRecalculationResultWhenPaymentCatchUpFails() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PurchaseHistoryRepository purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        SettlementHistoryRepository settlementHistoryRepository = mock(SettlementHistoryRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementPaymentService paymentService = mock(SettlementPaymentService.class);
        SettlementRecalculationService service = service(
                selectorsRepository, purchaseHistoryRepository, settlementHistoryRepository,
                worker, paymentService,
                Instant.parse("2026-09-01T01:00:00Z"));

        when(selectorsRepository.existsById(10L)).thenReturn(true);
        when(worker.calculate(
                10L, YearMonth.of(2026, 6), true, false))
                .thenReturn(new SettlementCalculationResult(null, SettlementCalculationOutcome.SKIPPED));
        doThrow(new IllegalStateException("payment failed"))
                .when(paymentService).processCurrentPaymentMonth();

        var result = service.recalculate(YearMonth.of(2026, 6), 10L);

        assertThat(result.skippedCount()).isEqualTo(1);
        verify(paymentService).processCurrentPaymentMonth();
    }

    @Test
    void recalculatesSelectedSelectorsForRequestedMonth() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PurchaseHistoryRepository purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        SettlementHistoryRepository settlementHistoryRepository = mock(SettlementHistoryRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementPaymentService paymentService = mock(SettlementPaymentService.class);
        SettlementRecalculationService service = service(
                selectorsRepository, purchaseHistoryRepository, settlementHistoryRepository,
                worker, paymentService,
                Instant.parse("2026-08-15T01:00:00Z"));

        when(selectorsRepository.existsById(10L)).thenReturn(true);
        when(worker.calculate(
                10L,
                YearMonth.of(2026, 7),
                false,
                false))
                .thenReturn(new SettlementCalculationResult(null, SettlementCalculationOutcome.UPDATED));

        var result = service.recalculate(YearMonth.of(2026, 7), 10L);

        assertThat(result.selectorsId()).isEqualTo(10L);
        assertThat(result.requestedActivityMonth()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.finalizedCount()).isZero();
        verify(paymentService).processCurrentPaymentMonth();
    }

    @Test
    void rejectsCurrentOrFutureMonth() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PurchaseHistoryRepository purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        SettlementHistoryRepository settlementHistoryRepository = mock(SettlementHistoryRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementPaymentService paymentService = mock(SettlementPaymentService.class);
        SettlementRecalculationService service = service(
                selectorsRepository, purchaseHistoryRepository, settlementHistoryRepository,
                worker, paymentService,
                Instant.parse("2026-08-15T01:00:00Z"));

        assertThatThrownBy(() -> service.recalculate(YearMonth.of(2026, 8), null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verifyNoInteractions(selectorsRepository, purchaseHistoryRepository,
                settlementHistoryRepository, worker);
        verify(paymentService, never()).processCurrentPaymentMonth();
    }

    private SettlementRecalculationService service(
            SelectorsRepository selectorsRepository,
            PurchaseHistoryRepository purchaseHistoryRepository,
            SettlementHistoryRepository settlementHistoryRepository,
            SettlementCalculationWorker worker,
            SettlementPaymentService paymentService,
            Instant instant) {
        Clock clock = Clock.fixed(instant, TimeConfig.SEOUL_ZONE);
        return new SettlementRecalculationService(
                selectorsRepository,
                purchaseHistoryRepository,
                settlementHistoryRepository,
                worker,
                new SettlementSchedulePolicy(),
                paymentService,
                clock);
    }
}
