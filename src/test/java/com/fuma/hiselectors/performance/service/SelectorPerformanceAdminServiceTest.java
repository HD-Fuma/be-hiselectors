package com.fuma.hiselectors.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.ConfirmedSales;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.GenerationMembership;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelection;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceRewardType;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelectionType;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceSelectionRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectorPerformanceAdminServiceTest {

    private final SelectorPerformanceQueryRepository repository =
            mock(SelectorPerformanceQueryRepository.class);
    private final SelectorExcellenceSelectionRepository selectionRepository =
            mock(SelectorExcellenceSelectionRepository.class);
    private SelectorPerformanceAdminService service;

    @BeforeEach
    void setUp() {
        service = new SelectorPerformanceAdminService(repository, selectionRepository);
    }

    @Test
    void sortsPeriodSalesAndReadsTheLatestPersistedExcellentSelections() {
        List<Selectors> selectors = List.of(
                selector(1L, "SEL-1", "알파"),
                selector(2L, "SEL-2", "베타"),
                selector(3L, "SEL-3", "감마"),
                selector(4L, "SEL-4", "델타"));
        when(repository.findAllVisibleSelectors()).thenReturn(selectors);
        when(repository.findGenerationMemberships(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(
                        generation(1L, 11L, "5기"),
                        generation(1L),
                        generation(2L),
                        generation(3L),
                        generation(4L)));
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        when(repository.summarizeConfirmedSales(
                List.of(1L, 2L, 3L, 4L),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        sales(4L, "200000", 2L),
                        sales(2L, "100000", 1L)));
        when(selectionRepository.findAllForSelectorsOrderByGenerationActivityEndDateDesc(
                List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(
                        selection(1L, SelectorExcellenceSelectionType.SALES_RANKING, 1),
                        selection(1L, SelectorExcellenceSelectionType.SALES_THRESHOLD, null),
                        selection(2L, SelectorExcellenceSelectionType.SALES_RANKING, 2),
                        selection(3L, SelectorExcellenceSelectionType.SALES_RANKING, 3),
                        selection(2L, 9L,
                                SelectorExcellenceSelectionType.SALES_THRESHOLD, null)));

        var result = service.getSelectorPerformance(null, startDate, endDate);

        assertThat(result).extracting(item -> item.selectorId())
                .containsExactly(4L, 2L, 1L, 3L);
        assertThat(result.getFirst().totalSales()).isEqualByComparingTo("200000");
        assertThat(result.getFirst().isExcellent()).isFalse();
        assertThat(result.get(1).excellentActivityType()).isEqualTo("4기 활동 누적 2위");
        assertThat(result.get(2).excellentActivityType())
                .isEqualTo("4기 활동 누적 1위 · 누적 매출 1,000만원 이상 달성");
        assertThat(result.get(2).generationName()).isEqualTo("5기");
        assertThat(result.get(2).excellentGenerationName()).isEqualTo("4기");
        assertThat(result.get(2).excellentGenerationSales())
                .isEqualByComparingTo("12500000");
        assertThat(result.getLast().excellentActivityType()).isEqualTo("4기 활동 누적 3위");
    }

    @Test
    void filtersByTrimmedCodeOrNicknameWithoutChangingGenerationRank() {
        List<Selectors> selectors = List.of(
                selector(1L, "SEL-ALPHA", "첫째"),
                selector(2L, "SEL-BETA", "베타"),
                selector(3L, "SEL-GAMMA", "셋째"),
                selector(4L, "SEL-DELTA", "넷째"));
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(repository.findAllVisibleSelectors()).thenReturn(selectors);
        when(repository.findGenerationMemberships(ids)).thenReturn(List.of(
                generation(1L), generation(2L), generation(3L), generation(4L)));
        when(repository.summarizeConfirmedSales(ids, null, null)).thenReturn(List.of(
                sales(1L, "400", 4L), sales(2L, "300", 3L),
                sales(3L, "200", 2L), sales(4L, "100", 1L)));
        when(selectionRepository.findAllForSelectorsOrderByGenerationActivityEndDateDesc(ids))
                .thenReturn(List.of(
                        selection(2L, SelectorExcellenceSelectionType.SALES_RANKING, 2)));

        var result = service.getSelectorPerformance("  beta ", null, null);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.selectorId()).isEqualTo(2L);
            assertThat(item.isExcellent()).isTrue();
            assertThat(item.excellentActivityType()).isEqualTo("4기 활동 누적 2위");
        });
    }

    @Test
    void hidesPersistedExcellenceFromBlacklistedSelectorWithoutRerankingOthers() {
        Selectors blacklisted = selector(1L, "SEL-BLACK", "블랙리스트", "BLACKLIST");
        when(blacklisted.isBlacklisted()).thenReturn(true);
        List<Selectors> selectors = List.of(
                blacklisted,
                selector(2L, "SEL-2", "둘째"),
                selector(3L, "SEL-3", "셋째"),
                selector(4L, "SEL-4", "넷째"));
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(repository.findAllVisibleSelectors()).thenReturn(selectors);
        when(repository.findGenerationMemberships(ids)).thenReturn(List.of(
                generation(1L), generation(2L), generation(3L), generation(4L)));
        when(repository.summarizeConfirmedSales(ids, null, null)).thenReturn(List.of(
                sales(1L, "20000000", 20L), sales(2L, "900", 9L),
                sales(3L, "800", 8L), sales(4L, "700", 7L)));
        when(selectionRepository.findAllForSelectorsOrderByGenerationActivityEndDateDesc(ids))
                .thenReturn(List.of(
                        selection(1L, SelectorExcellenceSelectionType.SALES_RANKING, 1),
                        selection(1L, SelectorExcellenceSelectionType.SALES_THRESHOLD, null),
                        selection(2L, SelectorExcellenceSelectionType.SALES_RANKING, 2),
                        selection(3L, SelectorExcellenceSelectionType.SALES_RANKING, 3)));

        var result = service.getSelectorPerformance(null, null, null);

        assertThat(result).filteredOn(item -> item.selectorId().equals(1L))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.isExcellent()).isFalse();
                    assertThat(item.excellentActivityType()).isNull();
                });
        assertThat(result).filteredOn(item -> item.selectorId().equals(2L))
                .singleElement()
                .satisfies(item -> assertThat(item.excellentActivityType())
                        .isEqualTo("4기 활동 누적 2위"));
        assertThat(result).filteredOn(item -> item.selectorId().equals(3L))
                .singleElement()
                .satisfies(item -> assertThat(item.excellentActivityType())
                        .isEqualTo("4기 활동 누적 3위"));
    }

    @Test
    void rejectsReversedDateRangeBeforeQuerying() {
        assertThatThrownBy(() -> service.getSelectorPerformance(
                null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("시작일은 종료일보다 늦을 수 없습니다.");

        verify(repository, never()).findAllVisibleSelectors();
    }

    private Selectors selector(Long id, String code, String nickname) {
        return selector(id, code, nickname, "ACTIVE");
    }

    private Selectors selector(Long id, String code, String nickname, String roleId) {
        Selectors selector = mock(Selectors.class);
        when(selector.getId()).thenReturn(id);
        when(selector.getSelectorsCode()).thenReturn(code);
        when(selector.getSelectorsNickname()).thenReturn(nickname);
        when(selector.getSelectorsRoleId()).thenReturn(roleId);
        return selector;
    }

    private GenerationMembership generation(Long selectorId) {
        return generation(selectorId, 10L, "4기");
    }

    private GenerationMembership generation(
            Long selectorId, Long generationId, String generationName) {
        return new GenerationMembership(selectorId, generationId, generationName);
    }

    private ConfirmedSales sales(Long selectorId, String amount, long orderCount) {
        return new ConfirmedSales(selectorId, new BigDecimal(amount), orderCount);
    }

    private SelectorExcellenceSelection selection(
            Long selectorId, SelectorExcellenceSelectionType type, Integer rank) {
        return selection(selectorId, 10L, type, rank);
    }

    private SelectorExcellenceSelection selection(
            Long selectorId,
            Long generationId,
            SelectorExcellenceSelectionType type,
            Integer rank) {
        return SelectorExcellenceSelection.create(
                generationId,
                selectorId,
                type,
                new BigDecimal("12500000"),
                0L,
                rank,
                SelectorExcellenceRewardType.H_POINT,
                0L,
                0,
                LocalDateTime.of(2026, 8, 1, 0, 0));
    }
}
