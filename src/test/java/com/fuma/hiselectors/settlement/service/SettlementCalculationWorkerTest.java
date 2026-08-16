package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.purchase.repository.PurchaseSettlementSummary;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementSourceCode;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementCalculationWorkerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final YearMonth JULY = YearMonth.of(2026, 7);

    private SelectorsRepository selectorsRepository;
    private ApplicationRepository applicationRepository;
    private PurchaseHistoryRepository purchaseHistoryRepository;
    private SettlementHistoryRepository settlementHistoryRepository;
    private SettlementCalculationWorker worker;

    @BeforeEach
    void setUp() {
        selectorsRepository = mock(SelectorsRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        settlementHistoryRepository = mock(SettlementHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T18:00:00Z"), SEOUL);
        worker = new SettlementCalculationWorker(
                selectorsRepository,
                applicationRepository,
                purchaseHistoryRepository,
                settlementHistoryRepository,
                new CommissionRateCalculator(),
                clock);
        givenRateSource(SnsPlatform.YOUTUBE, 5_000L);
        when(settlementHistoryRepository.save(any(SettlementHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void calculatesAndFinalizesPreviousMonth() {
        when(settlementHistoryRepository.findBySelectorsIdAndSettlementMonth(
                1L, LocalDateTime.of(2026, 7, 1, 0, 0)))
                .thenReturn(Optional.empty());
        when(purchaseHistoryRepository.summarizeConfirmedPurchasesForActivityMonth(
                1L,
                PurchaseStatus.PURCHASE_CONFIRMED,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0)))
                .thenReturn(summary("12345.00", 2L));

        SettlementCalculationResult result = worker.calculate(
                1L, JULY, SettlementSourceCode.DAILY_BATCH, true);

        SettlementHistory history = result.settlementHistory();
        assertThat(result.outcome()).isEqualTo(SettlementCalculationOutcome.FINALIZED);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_PENDING);
        assertThat(history.getTotalSales()).isEqualTo(12_345L);
        assertThat(history.getConfirmedPurchaseCount()).isEqualTo(2L);
        assertThat(history.getCommissionRate()).isEqualByComparingTo("3.00");
        assertThat(history.getCommission()).isEqualTo(370L);
    }

    @Test
    void rejectsFractionalWonWithoutSaving() {
        when(settlementHistoryRepository.findBySelectorsIdAndSettlementMonth(any(), any()))
                .thenReturn(Optional.empty());
        when(purchaseHistoryRepository.summarizeConfirmedPurchasesForActivityMonth(
                any(), any(), any(), any()))
                .thenReturn(summary("100.50", 1L));

        assertThatThrownBy(() -> worker.calculate(
                1L, JULY, SettlementSourceCode.DAILY_BATCH, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SETTLEMENT_AMOUNT);
        verify(settlementHistoryRepository, never()).save(any());
    }

    @Test
    void skipsProtectedHistoryWithoutRecalculation() {
        SettlementHistory history = SettlementHistory.create(
                1L, LocalDateTime.of(2026, 7, 1, 0, 0));
        history.updateCalculation(
                100L, 1L, new BigDecimal("3.00"), 3L,
                SettlementSourceCode.DAILY_BATCH, LocalDateTime.now());
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.now());
        when(settlementHistoryRepository.findBySelectorsIdAndSettlementMonth(any(), any()))
                .thenReturn(Optional.of(history));

        SettlementCalculationResult result = worker.calculate(
                1L, JULY, SettlementSourceCode.DAILY_BATCH, false);

        assertThat(result.outcome()).isEqualTo(SettlementCalculationOutcome.SKIPPED);
        verifyNoInteractions(applicationRepository, purchaseHistoryRepository);
    }

    @Test
    void forceRecalculatesPaymentPendingHistoryAndKeepsItPaymentPending() {
        SettlementHistory history = SettlementHistory.create(
                1L, LocalDateTime.of(2026, 7, 1, 0, 0));
        history.updateCalculation(
                0L, 0L, new BigDecimal("3.00"), 0L,
                SettlementSourceCode.DAILY_BATCH, LocalDateTime.now());
        history.transitionTo(SettlementStatus.PAYMENT_PENDING, LocalDateTime.now());
        when(settlementHistoryRepository.findBySelectorsIdAndSettlementMonth(
                1L, LocalDateTime.of(2026, 7, 1, 0, 0)))
                .thenReturn(Optional.of(history));
        when(purchaseHistoryRepository.summarizeConfirmedPurchasesForActivityMonth(
                1L,
                PurchaseStatus.PURCHASE_CONFIRMED,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 0)))
                .thenReturn(summary("10000.00", 1L));

        SettlementCalculationResult result = worker.calculate(
                1L, JULY, SettlementSourceCode.DAILY_BATCH, true, true);

        assertThat(result.outcome()).isEqualTo(SettlementCalculationOutcome.FINALIZED);
        assertThat(history.getStatus()).isEqualTo(SettlementStatus.PAYMENT_PENDING);
        assertThat(history.getTotalSales()).isEqualTo(10_000L);
    }

    private void givenRateSource(SnsPlatform platform, Long followerCount) {
        Selectors selectors = mock(Selectors.class);
        Application application = mock(Application.class);
        when(selectors.getApplicationId()).thenReturn(10L);
        when(application.getSnsCode()).thenReturn(platform);
        when(application.getFollowerCount()).thenReturn(followerCount);
        when(selectorsRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(selectors));
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(application));
    }

    private PurchaseSettlementSummary summary(String totalSales, long count) {
        PurchaseSettlementSummary summary = mock(PurchaseSettlementSummary.class);
        when(summary.getTotalSales()).thenReturn(new BigDecimal(totalSales));
        when(summary.getConfirmedPurchaseCount()).thenReturn(count);
        return summary;
    }
}
