package com.fuma.hiselectors.performance.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.performance.dto.SelectorBreakdownResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceTrendResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceTrendResponse.Bucket;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceTrendResponse.Point;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.ConfirmedSales;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.DatedSales;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.GenerationMembership;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.SelectorCount;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.SelectorSnsProfile;
import com.fuma.hiselectors.performance.service.SelectorPerformanceDashboardCalculator.SelectorSnapshot;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelection;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelectionType;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceSelectionRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.service.CommissionRateCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelectorPerformanceAdminService {

    private static final String SALES_ACHIEVEMENT = "누적 매출 1,000만원 이상 달성";
    private static final int DAY_BUCKET_MAX_DAYS = 31;
    private static final int DEFAULT_TREND_MONTHS = 6;

    private final SelectorPerformanceQueryRepository queryRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorExcellenceSelectionRepository excellenceSelectionRepository;
    private final GenerationRepository generationRepository;
    private final CommissionRateCalculator commissionRateCalculator;
    private final Clock clock;

    public List<SelectorPerformanceResponse> getSelectorPerformance(
            String keyword, Long generationId, LocalDate startDate, LocalDate endDate) {
        Period period = Period.of(startDate, endDate);
        List<Selectors> selectors = generationId == null
                ? queryRepository.findAllVisibleSelectors()
                : queryRepository.findVisibleMembers(List.of(requireGeneration(generationId).getId()));
        if (selectors.isEmpty()) {
            return List.of();
        }

        List<Long> selectorIds = selectors.stream().map(Selectors::getId).toList();
        List<GenerationMembership> memberships =
                queryRepository.findGenerationMemberships(selectorIds);
        Map<Long, GenerationMembership> latestGenerations = latestGenerations(memberships);
        Map<Long, String> generationNames = generationNames(memberships);
        Map<Long, ConfirmedSales> displayedSales = salesBySelector(
                queryRepository.summarizeConfirmedSales(
                        selectorIds, period.startInclusive(), period.endExclusive()));
        Map<Long, LatestExcellence> latestExcellence = latestExcellence(
                excellenceSelectionRepository
                        .findAllForSelectorsOrderByGenerationActivityEndDateDesc(selectorIds));
        Map<Long, SelectorSnsProfile> profiles = snsProfiles(selectorIds);
        Map<Long, Long> clicks = countsBySelector(queryRepository.countProductClicks(
                selectorIds, period.startInclusive(), period.endExclusive()));
        Map<Long, Long> contents = countsBySelector(queryRepository.countContents(
                selectorIds, period.startInclusive(), period.endExclusive()));
        String normalizedKeyword = normalizeKeyword(keyword);

        return selectors.stream()
                .filter(selector -> matches(selector, normalizedKeyword))
                .map(selector -> toResponse(
                        selector,
                        latestGenerations.get(selector.getId()),
                        displayedSales.getOrDefault(selector.getId(), ConfirmedSales.ZERO),
                        latestExcellence.get(selector.getId()),
                        generationNames,
                        profiles.get(selector.getId()),
                        clicks.getOrDefault(selector.getId(), 0L),
                        contents.getOrDefault(selector.getId(), 0L)))
                .sorted(Comparator.comparing(
                                SelectorPerformanceResponse::totalSales,
                                Comparator.reverseOrder())
                        .thenComparing(
                                SelectorPerformanceResponse::confirmedOrderCount,
                                Comparator.reverseOrder())
                        .thenComparing(SelectorPerformanceResponse::selectorId))
                .toList();
    }

    public SelectorPerformanceTrendResponse getTrend(
            Long generationId, LocalDate startDate, LocalDate endDate) {
        Period.of(startDate, endDate);
        List<Generation> generations = resolveDashboardGenerations(generationId);
        List<Selectors> selectors = queryRepository.findVisibleMembers(generationIds(generations));
        List<Long> selectorIds = selectors.stream().map(Selectors::getId).toList();
        TrendWindow window = TrendWindow.of(startDate, endDate, LocalDate.now(clock));
        List<DatedSales> rows = window.bucket() == Bucket.DAY
                ? queryRepository.summarizeConfirmedSalesByDay(
                        selectorIds, window.startInclusive(), window.endExclusive())
                : queryRepository.summarizeConfirmedSalesByMonth(
                        selectorIds, window.startInclusive(), window.endExclusive());
        Map<LocalDate, DatedSales> byDate = rows.stream().collect(Collectors.toMap(
                DatedSales::date, Function.identity(), (left, ignored) -> left));
        List<Point> points = new ArrayList<>();
        for (LocalDate date = window.startDate();
                !date.isAfter(window.endDate());
                date = window.bucket() == Bucket.DAY ? date.plusDays(1) : date.plusMonths(1)) {
            DatedSales row = byDate.get(date);
            points.add(row == null
                    ? new Point(date, BigDecimal.ZERO, 0L)
                    : new Point(date, row.totalSales(), row.confirmedOrderCount()));
        }
        return new SelectorPerformanceTrendResponse(
                window.bucket(), window.startDate(), window.endDate(), List.copyOf(points));
    }

    public SelectorPerformanceSummaryResponse getSummary(
            Long generationId, LocalDate startDate, LocalDate endDate) {
        Period period = Period.of(startDate, endDate);
        List<Generation> generations = resolveDashboardGenerations(generationId);
        List<Long> generationIds = generationIds(generations);
        List<Selectors> selectors = queryRepository.findVisibleMembers(generationIds);
        Period previous = previousPeriod(generations, startDate);
        if (selectors.isEmpty()) {
            return SelectorPerformanceDashboardCalculator.summarize(
                    generationIds,
                    previous.startDate(),
                    previous.endDate(),
                    List.of());
        }
        List<Long> selectorIds = selectors.stream().map(Selectors::getId).toList();
        List<GenerationMembership> memberships =
                queryRepository.findGenerationMemberships(selectorIds);
        Map<Long, GenerationMembership> latestGenerations = latestGenerations(memberships);
        Map<Long, ConfirmedSales> currentSales = salesBySelector(
                queryRepository.summarizeConfirmedSales(
                        selectorIds, period.startInclusive(), period.endExclusive()));
        Map<Long, ConfirmedSales> previousSales = previous.isEmpty()
                ? Map.of()
                : salesBySelector(queryRepository.summarizeConfirmedSales(
                        selectorIds, previous.startInclusive(), previous.endExclusive()));
        Map<Long, SelectorSnsProfile> profiles = snsProfiles(selectorIds);
        Map<Long, Long> clicks = countsBySelector(queryRepository.countProductClicks(
                selectorIds, period.startInclusive(), period.endExclusive()));
        Map<Long, Long> contents = countsBySelector(queryRepository.countContents(
                selectorIds, period.startInclusive(), period.endExclusive()));
        List<SelectorSnapshot> snapshots = selectors.stream()
                .map(selector -> toSnapshot(
                        selector,
                        latestGenerations.get(selector.getId()),
                        currentSales.getOrDefault(selector.getId(), ConfirmedSales.ZERO),
                        previousSales.getOrDefault(selector.getId(), ConfirmedSales.ZERO),
                        profiles.get(selector.getId()),
                        clicks.getOrDefault(selector.getId(), 0L),
                        contents.getOrDefault(selector.getId(), 0L)))
                .toList();
        return SelectorPerformanceDashboardCalculator.summarize(
                generationIds, previous.startDate(), previous.endDate(), snapshots);
    }

    public SelectorBreakdownResponse getBreakdown(
            Long selectorId, LocalDate startDate, LocalDate endDate) {
        Period period = Period.of(startDate, endDate);
        Selectors selector = selectorsRepository.findById(selectorId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        List<SelectorBreakdownResponse.ProductPerformance> products =
                queryRepository.summarizeConfirmedSalesByProduct(
                                selectorId, period.startInclusive(), period.endExclusive())
                        .stream()
                        .map(row -> new SelectorBreakdownResponse.ProductPerformance(
                                row.productId(), row.productName(), row.brandName(),
                                row.thumbnailUrl(), row.category(), row.totalSales(),
                                row.confirmedOrderCount(), row.soldQuantity()))
                        .toList();
        List<SelectorBreakdownResponse.CampaignPerformance> campaigns =
                queryRepository.summarizeConfirmedSalesByCampaign(
                                selectorId, period.startInclusive(), period.endExclusive())
                        .stream()
                        .map(row -> new SelectorBreakdownResponse.CampaignPerformance(
                                row.campaignId(), row.title(), row.totalSales(),
                                row.confirmedOrderCount(), row.soldQuantity()))
                        .toList();
        return new SelectorBreakdownResponse(
                selector.getId(), selector.getSelectorsCode(), selector.getSelectorsNickname(),
                selector.getCategory(), products, campaigns);
    }

    private SelectorSnapshot toSnapshot(
            Selectors selector,
            GenerationMembership generation,
            ConfirmedSales current,
            ConfirmedSales previous,
            SelectorSnsProfile profile,
            long clicks,
            long contents) {
        BigDecimal rate = commissionRate(profile);
        return new SelectorSnapshot(
                selector.getId(),
                selector.getSelectorsNickname(),
                profile == null ? null : profile.profileImageUrl(),
                generation == null ? null : generation.generationId(),
                generation == null ? null : generation.generationName(),
                selector.getCategory(),
                current.totalSales(),
                current.confirmedOrderCount(),
                clicks,
                contents,
                SelectorPerformanceDashboardCalculator.accruedCommission(current.totalSales(), rate),
                previous.totalSales(),
                previous.confirmedOrderCount(),
                SelectorPerformanceDashboardCalculator.accruedCommission(
                        previous.totalSales(), rate));
    }

    private List<Generation> resolveDashboardGenerations(Long generationId) {
        if (generationId != null) {
            return List.of(requireGeneration(generationId));
        }
        return generationRepository.findAllByStatusOrderByActivityStartDateAscIdAsc(
                GenerationStatus.ACTIVE);
    }

    private Generation requireGeneration(Long generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GENERATION_NOT_FOUND));
    }

    private List<Long> generationIds(List<Generation> generations) {
        return generations.stream().map(Generation::getId).toList();
    }

    private Period previousPeriod(List<Generation> generations, LocalDate currentStartDate) {
        if (currentStartDate == null || generations.isEmpty()) {
            return Period.empty();
        }
        LocalDate activityStart = generations.stream()
                .map(generation -> generation.getActivityStartDate().toLocalDate())
                .min(LocalDate::compareTo)
                .orElse(null);
        if (activityStart == null) {
            return Period.empty();
        }
        LocalDate previousEnd = currentStartDate.minusDays(1);
        if (activityStart.isAfter(previousEnd)) {
            return Period.empty();
        }
        return Period.of(activityStart, previousEnd);
    }

    private Map<Long, GenerationMembership> latestGenerations(
            List<GenerationMembership> memberships) {
        Map<Long, GenerationMembership> result = new LinkedHashMap<>();
        for (GenerationMembership membership : memberships) {
            result.putIfAbsent(membership.selectorId(), membership);
        }
        return result;
    }

    private Map<Long, String> generationNames(List<GenerationMembership> memberships) {
        return memberships.stream().collect(Collectors.toMap(
                GenerationMembership::generationId,
                GenerationMembership::generationName,
                (existing, ignored) -> existing));
    }

    private Map<Long, ConfirmedSales> salesBySelector(List<ConfirmedSales> rows) {
        return rows.stream().collect(Collectors.toMap(
                ConfirmedSales::selectorId, Function.identity()));
    }

    private Map<Long, SelectorSnsProfile> snsProfiles(List<Long> selectorIds) {
        return queryRepository.findSnsProfiles(selectorIds).stream()
                .collect(Collectors.toMap(
                        SelectorSnsProfile::selectorId,
                        Function.identity(),
                        (existing, ignored) -> existing));
    }

    private Map<Long, Long> countsBySelector(List<SelectorCount> rows) {
        return rows.stream().collect(Collectors.toMap(
                SelectorCount::selectorId, SelectorCount::count));
    }

    private BigDecimal commissionRate(SelectorSnsProfile profile) {
        if (profile == null || profile.snsCode() == null) {
            return BigDecimal.ZERO;
        }
        return commissionRateCalculator.calculate(profile.snsCode(), profile.followerCount());
    }

    private Map<Long, LatestExcellence> latestExcellence(
            List<SelectorExcellenceSelection> selections) {
        Map<Long, Long> latestGenerationIds = new HashMap<>();
        Map<Long, LatestExcellence> result = new HashMap<>();
        for (SelectorExcellenceSelection selection : selections) {
            Long selectorId = selection.getSelectorsId();
            Long latestGenerationId = latestGenerationIds.putIfAbsent(
                    selectorId, selection.getGenerationId());
            if (latestGenerationId != null
                    && !latestGenerationId.equals(selection.getGenerationId())) {
                continue;
            }
            result.computeIfAbsent(
                            selectorId,
                            ignored -> new LatestExcellence(selection.getGenerationId()))
                    .include(selection);
        }
        return result;
    }

    private SelectorPerformanceResponse toResponse(
            Selectors selector,
            GenerationMembership generation,
            ConfirmedSales displayedSales,
            LatestExcellence storedExcellence,
            Map<Long, String> generationNames,
            SelectorSnsProfile profile,
            long clickCount,
            long contentCount) {
        LatestExcellence excellence = selector.isBlacklisted() ? null : storedExcellence;
        String excellentGenerationName = excellence == null
                ? null
                : generationNames.get(excellence.generationId);
        String excellentActivityType = excellence == null
                ? null
                : excellence.reason(excellentGenerationName);
        BigDecimal excellentGenerationSales = excellence == null
                ? null
                : excellence.generationSales;

        return new SelectorPerformanceResponse(
                selector.getId(),
                selector.getSelectorsCode(),
                selector.getSelectorsNickname(),
                selector.getSelectorsRoleId(),
                generation == null ? null : generation.generationId(),
                generation == null ? null : generation.generationName(),
                selector.getCategory(),
                profile == null ? null : profile.profileImageUrl(),
                excellentGenerationName,
                excellentGenerationSales,
                displayedSales.totalSales(),
                displayedSales.confirmedOrderCount(),
                clickCount,
                contentCount,
                SelectorPerformanceDashboardCalculator.accruedCommission(
                        displayedSales.totalSales(), commissionRate(profile)),
                excellence != null,
                excellentActivityType);
    }

    private boolean matches(Selectors selector, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        return containsIgnoreCase(selector.getSelectorsCode(), normalizedKeyword)
                || containsIgnoreCase(selector.getSelectorsNickname(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : keyword.trim().toLowerCase(Locale.ROOT);
    }

    private record Period(
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    ) {

        private static Period empty() {
            return new Period(null, null, null, null);
        }

        private static Period of(LocalDate startDate, LocalDate endDate) {
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
            }
            try {
                return new Period(
                        startDate,
                        endDate,
                        startDate == null ? null : startDate.atStartOfDay(),
                        endDate == null ? null : endDate.plusDays(1).atStartOfDay());
            } catch (DateTimeException exception) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간이 올바르지 않습니다.");
            }
        }

        private boolean isEmpty() {
            return startInclusive == null || endExclusive == null;
        }
    }

    private record TrendWindow(
            Bucket bucket,
            LocalDate startDate,
            LocalDate endDate
    ) {

        private static TrendWindow of(LocalDate startDate, LocalDate endDate, LocalDate today) {
            if (startDate != null && endDate != null) {
                long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
                if (days <= DAY_BUCKET_MAX_DAYS) {
                    return new TrendWindow(Bucket.DAY, startDate, endDate);
                }
                return new TrendWindow(
                        Bucket.MONTH,
                        startDate.withDayOfMonth(1),
                        YearMonth.from(endDate).atEndOfMonth());
            }
            LocalDate monthEnd = YearMonth.from(endDate == null ? today : endDate).atEndOfMonth();
            LocalDate monthStart = YearMonth.from(monthEnd)
                    .minusMonths(DEFAULT_TREND_MONTHS - 1L)
                    .atDay(1);
            return new TrendWindow(Bucket.MONTH, monthStart, monthEnd);
        }

        private LocalDateTime startInclusive() {
            return startDate.atStartOfDay();
        }

        private LocalDateTime endExclusive() {
            return endDate.plusDays(1).atStartOfDay();
        }
    }

    private static final class LatestExcellence {

        private final Long generationId;
        private BigDecimal generationSales;
        private Integer rank;
        private boolean salesThreshold;

        private LatestExcellence(Long generationId) {
            this.generationId = generationId;
        }

        private void include(SelectorExcellenceSelection selection) {
            if (generationSales == null) {
                generationSales = selection.getGenerationSales();
            }
            if (selection.getSelectionType() == SelectorExcellenceSelectionType.SALES_RANKING) {
                rank = selection.getRankNo();
            } else if (selection.getSelectionType()
                    == SelectorExcellenceSelectionType.SALES_THRESHOLD) {
                salesThreshold = true;
            }
        }

        private String reason(String generationName) {
            List<String> reasons = new ArrayList<>();
            if (rank != null) {
                String prefix = generationName == null ? "" : generationName + " ";
                reasons.add(prefix + "활동 누적 " + rank + "위");
            }
            if (salesThreshold) {
                reasons.add(SALES_ACHIEVEMENT);
            }
            return reasons.isEmpty() ? null : String.join(" · ", reasons);
        }
    }
}
