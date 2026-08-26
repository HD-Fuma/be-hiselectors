package com.fuma.hiselectors.performance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.TopSelector;
import com.fuma.hiselectors.performance.service.SelectorPerformanceDashboardCalculator.SelectorSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectorPerformanceDashboardCalculatorTest {

    @Test
    void includesZeroSalesInAverageAndMedian() {
        var summary = SelectorPerformanceDashboardCalculator.summarize(
                List.of(1L),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                List.of(
                        snapshot(1L, "100", 0, "0"),
                        snapshot(2L, "0", 0, "0"),
                        snapshot(3L, "50", 0, "0")));

        assertThat(summary.kpis().averageSales()).isEqualByComparingTo("50");
        assertThat(summary.kpis().medianSales()).isEqualByComparingTo("50");
        assertThat(summary.distribution().sellingSelectorCount()).isEqualTo(2L);
        assertThat(summary.distribution().zeroSalesSelectorCount()).isEqualTo(1L);
    }

    @Test
    void averagesTheTwoMiddleValuesForEvenMedian() {
        assertThat(SelectorPerformanceDashboardCalculator.median(List.of(
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("40"),
                new BigDecimal("80"))))
                .isEqualByComparingTo("30");
    }

    @Test
    void marksPreviousZeroSalesAsNewInsteadOfARank() {
        var summary = SelectorPerformanceDashboardCalculator.summarize(
                List.of(1L),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                List.of(
                        snapshot(1L, "300", 1, "0"),
                        snapshot(2L, "200", 1, "150"),
                        snapshot(3L, "100", 1, "250")));

        assertThat(summary.top5()).extracting(TopSelector::selectorId)
                .containsExactly(1L, 2L, 3L);
        assertThat(summary.top5().getFirst().previousRank()).isNull();
        assertThat(summary.top5().get(1).previousRank()).isEqualTo(2);
        assertThat(summary.top5().get(2).previousRank()).isEqualTo(1);
        assertThat(summary.watchlist().newTop10()).isEqualTo(1L);
    }

    @Test
    void ignoresRankMovementWhenPreviousPeriodIsMissing() {
        var summary = SelectorPerformanceDashboardCalculator.summarize(
                List.of(1L),
                null,
                null,
                List.of(snapshot(1L, "300", 1, "0")));

        assertThat(summary.top5().getFirst().previousRank()).isNull();
        assertThat(summary.watchlist().newTop10()).isEqualTo(0L);
        assertThat(summary.kpis().totalSalesChangeRate()).isNull();
    }

    @Test
    void countsSwingsOnlyWhenPreviousSalesAreAtLeastOneHundredThousand() {
        var summary = SelectorPerformanceDashboardCalculator.summarize(
                List.of(1L),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                List.of(
                        snapshot(1L, "40000", 0, "80000"),
                        snapshot(2L, "40000", 0, "100000"),
                        snapshot(3L, "250000", 0, "100000")));

        assertThat(summary.watchlist().salesDrop()).isEqualTo(1L);
        assertThat(summary.watchlist().salesSurge()).isEqualTo(1L);
    }

    @Test
    void treatsMissingClicksAsZeroConversionRate() {
        assertThat(SelectorPerformanceDashboardCalculator.conversionRate(8L, 0L))
                .isEqualByComparingTo("0.00");
        assertThat(SelectorPerformanceDashboardCalculator.conversionRate(1L, 25L))
                .isEqualByComparingTo("4.00");
    }

    @Test
    void floorsAccruedCommissionFromPercentRate() {
        assertThat(SelectorPerformanceDashboardCalculator.accruedCommission(
                new BigDecimal("1000"), new BigDecimal("3.00")))
                .isEqualByComparingTo("30");
        assertThat(SelectorPerformanceDashboardCalculator.accruedCommission(
                new BigDecimal("100"), new BigDecimal("3.00")))
                .isEqualByComparingTo("3");
        assertThat(SelectorPerformanceDashboardCalculator.accruedCommission(
                new BigDecimal("10"), new BigDecimal("3.00")))
                .isEqualByComparingTo("0");
    }

    @Test
    void marksSparseCategoriesAsReference() {
        var summary = SelectorPerformanceDashboardCalculator.summarize(
                List.of(1L),
                null,
                null,
                List.of(
                        snapshot(1L, "BEAUTY", "100"),
                        snapshot(2L, "BEAUTY", "50"),
                        snapshot(3L, "FASHION", "80")));

        assertThat(summary.categories()).hasSize(2);
        assertThat(summary.categories().getFirst().category()).isEqualTo("FASHION");
        assertThat(summary.categories().getFirst().averageSales()).isEqualByComparingTo("80");
        assertThat(summary.categories().getLast().category()).isEqualTo("BEAUTY");
        assertThat(summary.categories().getLast().reference()).isTrue();
    }

    private SelectorSnapshot snapshot(
            Long id, String sales, long previousOrders, String previousSales) {
        return snapshot(id, "BEAUTY", sales, 1L, 0L, 0L, previousSales, previousOrders);
    }

    private SelectorSnapshot snapshot(Long id, String category, String sales) {
        return snapshot(id, category, sales, 0L, 0L, 0L, "0", 0L);
    }

    private SelectorSnapshot snapshot(
            Long id,
            String category,
            String sales,
            long orders,
            long clicks,
            long contents,
            String previousSales,
            long previousOrders) {
        return new SelectorSnapshot(
                id,
                "셀렉터" + id,
                null,
                1L,
                "4기",
                category,
                new BigDecimal(sales),
                orders,
                clicks,
                contents,
                BigDecimal.ZERO,
                new BigDecimal(previousSales),
                previousOrders,
                BigDecimal.ZERO);
    }
}
