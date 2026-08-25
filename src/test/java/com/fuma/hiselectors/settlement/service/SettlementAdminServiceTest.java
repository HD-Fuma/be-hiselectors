package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.config.TimeConfig;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementAccountRepository;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.settlement.security.SettlementAccountCrypto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementAdminServiceTest {

    @Test
    void getsSelectedSelectorsHistoriesInSettlementMonthDescendingOrder() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAdminService service = service(historyRepository, selectorsRepository);
        Selectors selectors = selectors(15L);
        SettlementHistory history = settlementHistory(15L);
        Pageable pageable = PageRequest.of(0, 12);

        when(selectorsRepository.findById(15L)).thenReturn(Optional.of(selectors));
        when(historyRepository.findAllBySelectorsIdOrderByActivityMonthDesc(15L, pageable))
                .thenReturn(new PageImpl<>(List.of(history), pageable, 1));

        var result = service.getHistories(15L, pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().getFirst().selectorsCode()).isEqualTo("SEL-0015");
        assertThat(result.getContent().getFirst().activityMonth()).isEqualTo(java.time.YearMonth.of(2026, 7));
        verify(historyRepository).findAllBySelectorsIdOrderByActivityMonthDesc(15L, pageable);
    }

    @Test
    void rejectsHistoryLookupForUnknownSelectors() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementAdminService service = service(historyRepository, selectorsRepository);

        when(selectorsRepository.findById(15L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistories(15L, PageRequest.of(0, 12)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SELECTOR_NOT_FOUND);
    }

    @Test
    void getsProfileSummaryAndMonthlyHistoriesForSettlementDetail() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SelectorsSnsAccountRepository snsAccountRepository = mock(SelectorsSnsAccountRepository.class);
        PurchaseHistoryRepository purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-15T01:00:00Z"), TimeConfig.SEOUL_ZONE);
        SettlementAdminService service = new SettlementAdminService(
                historyRepository, mock(SettlementAccountRepository.class), selectorsRepository,
                snsAccountRepository,
                purchaseHistoryRepository, clock,
                mock(SettlementProvisionalEstimateService.class),
                mock(SettlementAccountCrypto.class));
        Selectors selectors = selectors(15L);
        SelectorsSnsAccount snsAccount = SelectorsSnsAccount.builder()
                .selectorsId(15L)
                .snsCode(SnsPlatform.YOUTUBE)
                .accountId("UC123")
                .followerCount(76_200L)
                .profileImageUrl("https://cdn.example.com/profile.jpg")
                .lastCollectedAt(LocalDateTime.of(2026, 8, 15, 9, 0))
                .build();
        SettlementHistory nextPaymentHistory = settlementHistory(15L);
        nextPaymentHistory.transitionTo(
                SettlementStatus.PAYMENT_PENDING, LocalDateTime.of(2026, 8, 2, 0, 0));
        Pageable pageable = PageRequest.of(0, 12);

        when(selectorsRepository.findById(15L)).thenReturn(Optional.of(selectors));
        when(snsAccountRepository
                .findBySelectorsIdAndDeletedFalse(15L))
                .thenReturn(Optional.of(snsAccount));
        when(purchaseHistoryRepository.countDistinctOrdersBySelectorsIdAndStatusIn(
                15L, List.of(PurchaseStatus.PURCHASED, PurchaseStatus.PURCHASE_CONFIRMED)))
                .thenReturn(11L);
        when(purchaseHistoryRepository.countDistinctOrdersBySelectorsIdAndStatusInAndPurchasedAtBetween(
                15L,
                List.of(PurchaseStatus.PURCHASED, PurchaseStatus.PURCHASE_CONFIRMED),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0))).thenReturn(2L);
        when(historyRepository.sumCommissionBySelectorsIdAndStatus(15L, SettlementStatus.SETTLED))
                .thenReturn(1_500L);
        when(historyRepository.findAllBySelectorsIdAndStatus(15L, SettlementStatus.PAYMENT_PENDING))
                .thenReturn(List.of(nextPaymentHistory));
        when(historyRepository.findAllBySelectorsIdAndStatusIn(
                15L, List.of(
                        SettlementStatus.PAYMENT_HOLD_BLACK,
                        SettlementStatus.PAYMENT_HOLD_INFO,
                        SettlementStatus.PAYMENT_PENDING,
                        SettlementStatus.CALCULATING)))
                .thenReturn(List.of(nextPaymentHistory));
        when(historyRepository.sumSalesBySelectorsId(15L)).thenReturn(10_000L);
        when(historyRepository.findAllBySelectorsIdOrderByActivityMonthDesc(15L, pageable))
                .thenReturn(new PageImpl<>(List.of(nextPaymentHistory), pageable, 1));

        var result = service.getDetail(15L, pageable);

        assertThat(result.profile().selectorsCode()).isEqualTo("SEL-0015");
        assertThat(result.accountRegistered()).isFalse();
        assertThat(result.profile().followerCount()).isEqualTo(76_200L);
        assertThat(result.settlementSummary().cumulativePurchaseConversionCount()).isEqualTo(11L);
        assertThat(result.settlementSummary().cumulativePaidCommission()).isEqualTo(1_500L);
        assertThat(result.settlementSummary().currentMonthPurchaseConversionCount()).isEqualTo(2L);
        assertThat(result.settlementSummary().nextMonthScheduledCommission()).isEqualTo(300L);
        assertThat(result.settlementSummary().cumulativeSalesAmount()).isEqualTo(10_000L);
        assertThat(result.settlementSummary().nextPaymentMonth())
                .isEqualTo(java.time.YearMonth.of(2026, 8));
        assertThat(result.settlementSummary().nextPaymentSettlementStatus())
                .isEqualTo(SettlementStatus.PAYMENT_PENDING);
        assertThat(result.histories()).hasSize(1);
    }

    @Test
    void summarizesAllMatchingRowsWithWeightedRateAndHandlesZeroSales() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementAdminService service = service(historyRepository, mock(SelectorsRepository.class));
        SettlementHistoryRepository.SettlementAggregate aggregate = aggregate(2L, 7L, 100_000L, 5_500L);
        SettlementHistoryRepository.SettlementAggregate zeroSales = aggregate(0L, 0L, 0L, 0L);
        int activityYearMonth = 202607;
        when(historyRepository.summarize(
                activityYearMonth, 15L, SettlementStatus.SETTLED)).thenReturn(aggregate, zeroSales);

        var result = service.summarize(
                YearMonth.of(2026, 7), 15L, SettlementStatus.SETTLED);
        var zeroSalesResult = service.summarize(
                YearMonth.of(2026, 7), 15L, SettlementStatus.SETTLED);

        assertThat(result.settlementCount()).isEqualTo(2L);
        assertThat(result.confirmedPurchaseCount()).isEqualTo(7L);
        assertThat(result.confirmedSalesAmount()).isEqualTo(100_000L);
        assertThat(result.settlementAmount()).isEqualTo(5_500L);
        assertThat(result.commissionToSalesRate()).isEqualByComparingTo("5.50");
        assertThat(zeroSalesResult.commissionToSalesRate()).isEqualByComparingTo("0.00");
    }

    private SettlementAdminService service(
            SettlementHistoryRepository historyRepository, SelectorsRepository selectorsRepository) {
        return new SettlementAdminService(
                historyRepository,
                mock(SettlementAccountRepository.class),
                selectorsRepository,
                mock(SelectorsSnsAccountRepository.class),
                mock(PurchaseHistoryRepository.class),
                Clock.systemUTC(),
                mock(SettlementProvisionalEstimateService.class),
                mock(SettlementAccountCrypto.class));
    }

    private Selectors selectors(Long selectorsId) {
        Selectors selectors = Selectors.builder()
                .selectorsCode("SEL-0015")
                .selectorsNickname("박도윤")
                .selectorsRoleId("SELECTORS")
                .build();
        ReflectionTestUtils.setField(selectors, "id", selectorsId);
        return selectors;
    }

    private SettlementHistory settlementHistory(Long selectorsId) {
        SettlementHistory history = SettlementHistory.create(
                selectorsId, LocalDateTime.of(2026, 7, 1, 0, 0));
        history.updateCalculation(
                10_000L,
                2L,
                new BigDecimal("3.00"),
                300L,
                LocalDateTime.of(2026, 8, 1, 3, 0));
        return history;
    }

    private SettlementHistoryRepository.SettlementAggregate aggregate(
            long settlementCount,
            long confirmedPurchaseCount,
            long confirmedSalesAmount,
            long settlementAmount) {
        SettlementHistoryRepository.SettlementAggregate aggregate =
                mock(SettlementHistoryRepository.SettlementAggregate.class);
        when(aggregate.getSettlementCount()).thenReturn(settlementCount);
        when(aggregate.getConfirmedPurchaseCount()).thenReturn(confirmedPurchaseCount);
        when(aggregate.getConfirmedSalesAmount()).thenReturn(confirmedSalesAmount);
        when(aggregate.getSettlementAmount()).thenReturn(settlementAmount);
        return aggregate;
    }
}
