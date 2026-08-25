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
                        SettlementStatus.PAYMENT_CARRYOVER,
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
                .isEqualTo(java.time.YearMonth.of(2026, 9));
        assertThat(result.settlementSummary().nextPaymentSettlementStatus())
                .isEqualTo(SettlementStatus.PAYMENT_PENDING);
        assertThat(result.histories()).hasSize(1);
    }

    @Test
    void summarizesCurrentMonthTrendAndStatusDistributionFromOneAggregateQuery() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementAdminService service = service(historyRepository, mock(SelectorsRepository.class));
        List<SettlementHistoryRepository.SettlementAggregate> aggregates = List.of(
                aggregate(202602, SettlementStatus.SETTLED, 1L, 1L, 10_000L, 100L),
                aggregate(202606, SettlementStatus.SETTLED, 1L, 2L, 90_000L, 9_000L),
                aggregate(202607, SettlementStatus.SETTLED, 2L, 7L, 100_000L, 5_000L),
                aggregate(202607, SettlementStatus.PAYMENT_HOLD_INFO,
                        1L, 1L, 20_000L, 500L));
        when(historyRepository.summarizeByMonthAndStatus(
                202602, 202607, 15L, null)).thenReturn(aggregates);

        var result = service.summarize(YearMonth.of(2026, 7), 15L, null);

        assertThat(result.settlementCount()).isEqualTo(3L);
        assertThat(result.confirmedPurchaseCount()).isEqualTo(8L);
        assertThat(result.confirmedSalesAmount()).isEqualTo(120_000L);
        assertThat(result.settlementAmount()).isEqualTo(5_500L);
        assertThat(result.commissionToSalesRate()).isEqualByComparingTo("4.58");
        assertThat(result.monthlyTrend())
                .extracting(trend -> trend.activityMonth())
                .containsExactly(
                        YearMonth.of(2026, 2), YearMonth.of(2026, 3),
                        YearMonth.of(2026, 4), YearMonth.of(2026, 5),
                        YearMonth.of(2026, 6), YearMonth.of(2026, 7));
        assertThat(result.monthlyTrend().subList(1, 4))
                .allSatisfy(trend -> {
                    assertThat(trend.settlementCount()).isZero();
                    assertThat(trend.confirmedSalesAmount()).isZero();
                    assertThat(trend.settlementAmount()).isZero();
                    assertThat(trend.commissionToSalesRate()).isEqualByComparingTo("0.00");
                });
        assertThat(result.monthlyTrend().getLast().commissionToSalesRate())
                .isEqualByComparingTo("4.58");
        assertThat(result.statusDistribution())
                .extracting(distribution -> distribution.status())
                .containsExactly(SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.SETTLED);
        assertThat(result.statusDistribution().getFirst().settlementAmount()).isEqualTo(500L);
        assertThat(result.statusDistribution().getLast().settlementCount()).isEqualTo(2L);
        verify(historyRepository).summarizeByMonthAndStatus(
                202602, 202607, 15L, null);
    }

    @Test
    void keepsStatusFilterAndHandlesZeroSales() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementAdminService service = service(historyRepository, mock(SelectorsRepository.class));
        SettlementHistoryRepository.SettlementAggregate zeroSales = aggregate(
                202607, SettlementStatus.SETTLED, 1L, 0L, 0L, 0L);
        when(historyRepository.summarizeByMonthAndStatus(
                202602, 202607, 15L, SettlementStatus.SETTLED))
                .thenReturn(List.of(zeroSales));

        var result = service.summarize(
                YearMonth.of(2026, 7), 15L, SettlementStatus.SETTLED);

        assertThat(result.commissionToSalesRate()).isEqualByComparingTo("0.00");
        assertThat(result.monthlyTrend()).hasSize(6);
        assertThat(result.statusDistribution()).singleElement().satisfies(distribution -> {
            assertThat(distribution.status()).isEqualTo(SettlementStatus.SETTLED);
            assertThat(distribution.settlementCount()).isEqualTo(1L);
            assertThat(distribution.settlementAmount()).isZero();
        });
        verify(historyRepository).summarizeByMonthAndStatus(
                202602, 202607, 15L, SettlementStatus.SETTLED);
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
            int activityYearMonth,
            SettlementStatus status,
            long settlementCount,
            long confirmedPurchaseCount,
            long confirmedSalesAmount,
            long settlementAmount) {
        SettlementHistoryRepository.SettlementAggregate aggregate =
                mock(SettlementHistoryRepository.SettlementAggregate.class);
        when(aggregate.getActivityYearMonth()).thenReturn(activityYearMonth);
        when(aggregate.getStatus()).thenReturn(status);
        when(aggregate.getSettlementCount()).thenReturn(settlementCount);
        when(aggregate.getConfirmedPurchaseCount()).thenReturn(confirmedPurchaseCount);
        when(aggregate.getConfirmedSalesAmount()).thenReturn(confirmedSalesAmount);
        when(aggregate.getSettlementAmount()).thenReturn(settlementAmount);
        return aggregate;
    }
}
