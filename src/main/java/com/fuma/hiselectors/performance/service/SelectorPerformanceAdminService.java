package com.fuma.hiselectors.performance.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceResponse;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.ConfirmedSales;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.GenerationMembership;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelection;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelectionType;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceSelectionRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final SelectorPerformanceQueryRepository queryRepository;
    private final SelectorExcellenceSelectionRepository excellenceSelectionRepository;

    public List<SelectorPerformanceResponse> getSelectorPerformance(
            String keyword, LocalDate startDate, LocalDate endDate) {
        Period period = Period.of(startDate, endDate);
        List<Selectors> selectors = queryRepository.findAllVisibleSelectors();
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
        String normalizedKeyword = normalizeKeyword(keyword);

        return selectors.stream()
                .filter(selector -> matches(selector, normalizedKeyword))
                .map(selector -> toResponse(
                        selector,
                        latestGenerations.get(selector.getId()),
                        displayedSales.getOrDefault(selector.getId(), ConfirmedSales.ZERO),
                        latestExcellence.get(selector.getId()),
                        generationNames))
                .sorted(Comparator.comparing(
                                SelectorPerformanceResponse::totalSales,
                                Comparator.reverseOrder())
                        .thenComparing(
                                SelectorPerformanceResponse::confirmedOrderCount,
                                Comparator.reverseOrder())
                        .thenComparing(SelectorPerformanceResponse::selectorId))
                .toList();
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
            Map<Long, String> generationNames) {
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
                generation == null ? null : generation.generationName(),
                excellentGenerationName,
                excellentGenerationSales,
                displayedSales.totalSales(),
                displayedSales.confirmedOrderCount(),
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

    private record Period(LocalDateTime startInclusive, LocalDateTime endExclusive) {

        private static Period of(LocalDate startDate, LocalDate endDate) {
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
            }
            try {
                return new Period(
                        startDate == null ? null : startDate.atStartOfDay(),
                        endDate == null ? null : endDate.plusDays(1).atStartOfDay());
            } catch (DateTimeException exception) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간이 올바르지 않습니다.");
            }
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
