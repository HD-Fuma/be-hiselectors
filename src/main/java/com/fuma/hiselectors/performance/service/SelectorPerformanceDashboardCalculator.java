package com.fuma.hiselectors.performance.service;

import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.CategoryPerformance;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.Distribution;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.Kpis;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.SalesBucket;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.TopSelector;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.Universe;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse.Watchlist;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SelectorPerformanceDashboardCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE_HUNDRED_THOUSAND = new BigDecimal("100000");
    private static final BigDecimal FIVE_HUNDRED_THOUSAND = new BigDecimal("500000");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final int REFERENCE_CATEGORY_MIN = 5;
    private static final int TOP_LIST_SIZE = 5;
    private static final int NEW_TOP_THRESHOLD = 10;
    private static final int SMALL_UNIVERSE = 50;
    private static final int SMALL_UNIVERSE_TOP = 10;

    private SelectorPerformanceDashboardCalculator() {
    }

    static SelectorPerformanceSummaryResponse summarize(
            List<Long> generationIds,
            LocalDate previousStartDate,
            LocalDate previousEndDate,
            List<SelectorSnapshot> snapshots) {
        boolean comparable = previousStartDate != null && previousEndDate != null;
        List<SelectorSnapshot> ranked = snapshots.stream()
                .sorted(salesOrder())
                .toList();
        Map<Long, Integer> currentRanks = ranks(ranked, false);
        Map<Long, Integer> previousRanks = comparable
                ? ranks(snapshots.stream().sorted(previousSalesOrder()).toList(), true)
                : Map.of();

        return new SelectorPerformanceSummaryResponse(
                new Universe(
                        snapshots.size(),
                        List.copyOf(generationIds),
                        previousStartDate,
                        previousEndDate),
                toKpis(snapshots, comparable),
                toDistribution(ranked),
                toTop5(ranked, currentRanks, previousRanks, comparable),
                toCategories(snapshots),
                toWatchlist(ranked, currentRanks, previousRanks, comparable));
    }

    static BigDecimal accruedCommission(BigDecimal sales, BigDecimal ratePercent) {
        if (sales == null || ratePercent == null
                || sales.compareTo(BigDecimal.ZERO) <= 0
                || ratePercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return sales.multiply(ratePercent).divide(ONE_HUNDRED, 0, RoundingMode.FLOOR);
    }

    static BigDecimal conversionRate(long confirmedOrderCount, long clickCount) {
        if (clickCount == 0L) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(confirmedOrderCount)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(clickCount), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1)
                .add(sorted.get(middle))
                .divide(new BigDecimal("2"), 0, RoundingMode.HALF_UP);
    }

    static String salesBucketKey(BigDecimal sales) {
        if (sales == null || sales.compareTo(BigDecimal.ZERO) <= 0) {
            return "ZERO";
        }
        if (sales.compareTo(ONE_HUNDRED_THOUSAND) <= 0) {
            return "UP_TO_100000";
        }
        if (sales.compareTo(FIVE_HUNDRED_THOUSAND) <= 0) {
            return "UP_TO_500000";
        }
        if (sales.compareTo(ONE_MILLION) <= 0) {
            return "UP_TO_1000000";
        }
        return "OVER_1000000";
    }

    private static Kpis toKpis(List<SelectorSnapshot> snapshots, boolean comparable) {
        BigDecimal totalSales = sum(snapshots, SelectorSnapshot::sales);
        long orders = snapshots.stream().mapToLong(SelectorSnapshot::orders).sum();
        long clicks = snapshots.stream().mapToLong(SelectorSnapshot::clicks).sum();
        BigDecimal commission = sum(snapshots, SelectorSnapshot::accruedCommission);
        BigDecimal average = average(totalSales, snapshots.size());
        BigDecimal medianSales = median(snapshots.stream().map(SelectorSnapshot::sales).toList());
        BigDecimal previousSales = comparable
                ? sum(snapshots, SelectorSnapshot::previousSales) : null;
        Long previousOrders = comparable
                ? snapshots.stream().mapToLong(SelectorSnapshot::previousOrders).sum() : null;
        BigDecimal previousCommission = comparable
                ? sum(snapshots, SelectorSnapshot::previousCommission) : null;
        BigDecimal previousAverage = comparable
                ? average(previousSales, snapshots.size()) : null;
        return new Kpis(
                totalSales,
                orders,
                clicks,
                conversionRate(orders, clicks),
                commission,
                average,
                medianSales,
                previousSales,
                previousOrders,
                previousCommission,
                previousAverage,
                changeRate(totalSales, previousSales),
                changeRate(BigDecimal.valueOf(orders),
                        previousOrders == null ? null : BigDecimal.valueOf(previousOrders)),
                changeRate(commission, previousCommission),
                changeRate(average, previousAverage));
    }

    private static Distribution toDistribution(List<SelectorSnapshot> ranked) {
        long selling = ranked.stream().filter(row -> row.sales().signum() > 0).count();
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ZERO", 0L);
        counts.put("UP_TO_100000", 0L);
        counts.put("UP_TO_500000", 0L);
        counts.put("UP_TO_1000000", 0L);
        counts.put("OVER_1000000", 0L);
        for (SelectorSnapshot row : ranked) {
            String key = salesBucketKey(row.sales());
            counts.put(key, counts.get(key) + 1L);
        }
        List<SalesBucket> buckets = counts.entrySet().stream()
                .map(entry -> new SalesBucket(entry.getKey(), entry.getValue()))
                .toList();
        return new Distribution(
                selling,
                ranked.size() - selling,
                topShareRate(ranked),
                buckets);
    }

    private static BigDecimal topShareRate(List<SelectorSnapshot> rankedBySales) {
        if (rankedBySales.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal total = sum(rankedBySales, SelectorSnapshot::sales);
        if (total.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        int topCount = rankedBySales.size() < SMALL_UNIVERSE
                ? Math.min(SMALL_UNIVERSE_TOP, rankedBySales.size())
                : Math.max(1, (int) Math.ceil(rankedBySales.size() * 0.1d));
        BigDecimal topSales = sum(rankedBySales.subList(0, topCount), SelectorSnapshot::sales);
        return topSales.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static List<TopSelector> toTop5(
            List<SelectorSnapshot> ranked,
            Map<Long, Integer> currentRanks,
            Map<Long, Integer> previousRanks,
            boolean comparable) {
        return ranked.stream()
                .limit(TOP_LIST_SIZE)
                .map(row -> new TopSelector(
                        row.selectorId(),
                        row.nickname(),
                        row.profileImageUrl(),
                        row.generationName(),
                        row.sales(),
                        currentRanks.get(row.selectorId()),
                        comparable ? previousRanks.get(row.selectorId()) : null))
                .toList();
    }

    private static List<CategoryPerformance> toCategories(List<SelectorSnapshot> snapshots) {
        Map<String, List<SelectorSnapshot>> grouped = new LinkedHashMap<>();
        for (SelectorSnapshot row : snapshots) {
            grouped.computeIfAbsent(
                            Objects.requireNonNullElse(row.category(), ""),
                            ignored -> new ArrayList<>())
                    .add(row);
        }
        return grouped.entrySet().stream()
                .map(entry -> {
                    List<SelectorSnapshot> members = entry.getValue();
                    BigDecimal total = sum(members, SelectorSnapshot::sales);
                    return new CategoryPerformance(
                            entry.getKey().isEmpty() ? null : entry.getKey(),
                            members.size(),
                            average(total, members.size()),
                            median(members.stream().map(SelectorSnapshot::sales).toList()),
                            members.size() < REFERENCE_CATEGORY_MIN);
                })
                .sorted(Comparator.comparing(CategoryPerformance::averageSales).reversed()
                        .thenComparing(row -> Objects.requireNonNullElse(row.category(), "")))
                .toList();
    }

    private static Watchlist toWatchlist(
            List<SelectorSnapshot> ranked,
            Map<Long, Integer> currentRanks,
            Map<Long, Integer> previousRanks,
            boolean comparable) {
        long noClicks = 0L;
        long noUploads = 0L;
        long clicksWithoutPurchase = 0L;
        long salesDrop = 0L;
        long salesSurge = 0L;
        long newTop10 = 0L;
        for (SelectorSnapshot row : ranked) {
            if (row.clicks() == 0L) {
                noClicks++;
            }
            if (row.contents() == 0L) {
                noUploads++;
            }
            if (row.clicks() > 0L && row.orders() == 0L) {
                clicksWithoutPurchase++;
            }
            if (comparable && row.previousSales().compareTo(ONE_HUNDRED_THOUSAND) >= 0) {
                if (row.sales().multiply(new BigDecimal("2"))
                        .compareTo(row.previousSales()) <= 0) {
                    salesDrop++;
                }
                if (row.sales().compareTo(row.previousSales().multiply(new BigDecimal("2"))) >= 0) {
                    salesSurge++;
                }
            }
            Integer currentRank = currentRanks.get(row.selectorId());
            if (comparable
                    && currentRank != null
                    && currentRank <= NEW_TOP_THRESHOLD) {
                Integer previousRank = previousRanks.get(row.selectorId());
                if (previousRank == null || previousRank > NEW_TOP_THRESHOLD) {
                    newTop10++;
                }
            }
        }
        return new Watchlist(
                noClicks, noUploads, clicksWithoutPurchase, salesDrop, salesSurge, newTop10);
    }

    private static Map<Long, Integer> ranks(List<SelectorSnapshot> ordered, boolean requireSales) {
        Map<Long, Integer> ranks = new LinkedHashMap<>();
        int rank = 1;
        for (SelectorSnapshot row : ordered) {
            if (requireSales && row.previousSales().signum() <= 0) {
                continue;
            }
            ranks.put(row.selectorId(), rank);
            rank++;
        }
        return ranks;
    }

    private static Comparator<SelectorSnapshot> salesOrder() {
        return Comparator.comparing(SelectorSnapshot::sales).reversed()
                .thenComparing(Comparator.comparingLong(SelectorSnapshot::orders).reversed())
                .thenComparing(SelectorSnapshot::selectorId);
    }

    private static Comparator<SelectorSnapshot> previousSalesOrder() {
        return Comparator.comparing(SelectorSnapshot::previousSales).reversed()
                .thenComparing(Comparator.comparingLong(SelectorSnapshot::previousOrders).reversed())
                .thenComparing(SelectorSnapshot::selectorId);
    }

    private static BigDecimal sum(
            List<SelectorSnapshot> snapshots,
            java.util.function.Function<SelectorSnapshot, BigDecimal> getter) {
        return snapshots.stream()
                .map(getter)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal average(BigDecimal total, int count) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal changeRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(ONE_HUNDRED)
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    record SelectorSnapshot(
            Long selectorId,
            String nickname,
            String profileImageUrl,
            Long generationId,
            String generationName,
            String category,
            BigDecimal sales,
            long orders,
            long clicks,
            long contents,
            BigDecimal accruedCommission,
            BigDecimal previousSales,
            long previousOrders,
            BigDecimal previousCommission
    ) {
    }
}
