package com.fuma.hiselectors.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.DatedSelectorSales;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.SelectorSnsProfile;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.settlement.service.CommissionRateCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardSalesSettlementTrendServiceTest {

    private final SelectorPerformanceQueryRepository queryRepository =
            mock(SelectorPerformanceQueryRepository.class);
    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private DashboardSalesSettlementTrendService service;

    @BeforeEach
    void setUp() {
        service = new DashboardSalesSettlementTrendService(
                queryRepository, generationRepository, new CommissionRateCalculator());
    }

    @Test
    void returnsSevenDailySalesAndSettlementPointsWithMissingDaysFilled() {
        LocalDate startDate = LocalDate.of(2026, 8, 21);
        LocalDate endDate = LocalDate.of(2026, 8, 27);
        Generation generation = mock(Generation.class);
        Selectors instagram = mock(Selectors.class);
        Selectors youtube = mock(Selectors.class);
        when(generation.getId()).thenReturn(11L);
        when(instagram.getId()).thenReturn(1L);
        when(youtube.getId()).thenReturn(2L);
        when(generationRepository.findAllByStatusOrderByActivityStartDateAscIdAsc(
                GenerationStatus.ACTIVE)).thenReturn(List.of(generation));
        when(queryRepository.findVisibleMembers(List.of(11L)))
                .thenReturn(List.of(instagram, youtube));
        when(queryRepository.findSnsProfiles(List.of(1L, 2L))).thenReturn(List.of(
                new SelectorSnsProfile(
                        1L, null, SnsPlatform.INSTAGRAM, 12_000L),
                new SelectorSnsProfile(
                        2L, null, SnsPlatform.YOUTUBE, 6_000L)));
        when(queryRepository.summarizeConfirmedSalesBySelectorAndDay(
                List.of(1L, 2L), startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        new DatedSelectorSales(
                                1L, startDate, new BigDecimal("1000")),
                        new DatedSelectorSales(
                                2L, startDate, new BigDecimal("2000")),
                        new DatedSelectorSales(
                                1L, endDate, new BigDecimal("500"))));

        var result = service.getTrend(startDate, endDate);

        assertThat(result.startDate()).isEqualTo(startDate);
        assertThat(result.endDate()).isEqualTo(endDate);
        assertThat(result.points()).hasSize(7);
        assertThat(result.points().getFirst().salesAmount()).isEqualByComparingTo("3000");
        assertThat(result.points().getFirst().settlementAmount()).isEqualByComparingTo("150");
        assertThat(result.points().get(1).salesAmount()).isEqualByComparingTo("0");
        assertThat(result.points().getLast().salesAmount()).isEqualByComparingTo("500");
        assertThat(result.points().getLast().settlementAmount()).isEqualByComparingTo("25");
        verify(queryRepository).summarizeConfirmedSalesBySelectorAndDay(
                List.of(1L, 2L), startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    @Test
    void rejectsPeriodsLongerThanSevenDays() {
        assertThatThrownBy(() -> service.getTrend(
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 27)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("7일");
    }
}
