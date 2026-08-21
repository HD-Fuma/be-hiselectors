package com.fuma.hiselectors.performance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.DailyClick;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.DailyPurchase;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.ProductClick;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.ProductPurchase;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.PurchaseSummary;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.service.CommissionRateCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerformanceServiceTest {

    private final SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final CommissionRateCalculator commissionRateCalculator =
            mock(CommissionRateCalculator.class);
    private final PerformanceQueryRepository performanceQueryRepository =
            mock(PerformanceQueryRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-21T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    private PerformanceService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceService(
                selectorAccessService,
                applicationRepository,
                commissionRateCalculator,
                performanceQueryRepository,
                clock);
    }

    @Test
    void returnsSummaryUsingConfirmedPurchaseAndSettlementRules() {
        Selectors selectors = selector();
        Application application = application();
        when(selectorAccessService.requireCurrent("selector-user")).thenReturn(selectors);
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(application));
        when(commissionRateCalculator.calculate(SnsPlatform.INSTAGRAM, 20_000L))
                .thenReturn(new BigDecimal("3.00"));

        stubMetrics(YearMonth.of(2026, 8), 12_840L, 42_820_000L, 386L);
        stubMetrics(YearMonth.of(2026, 7), 10_000L, 30_000_000L, 300L);
        Period august = period(YearMonth.of(2026, 8));
        when(performanceQueryRepository.findDailyProductClicks(
                9L, august.start(), august.end()))
                .thenReturn(List.of(new DailyClick(1, 100L), new DailyClick(2, 200L)));
        when(performanceQueryRepository.findDailyConfirmedPurchases(
                9L, august.start(), august.end()))
                .thenReturn(List.of(
                        new DailyPurchase(1, 3L, new BigDecimal("300000")),
                        new DailyPurchase(2, 5L, new BigDecimal("500000"))));
        when(performanceQueryRepository.findProductClicks(
                9L, august.start(), august.end()))
                .thenReturn(List.of(
                        click(1L, 2_840L),
                        click(2L, 1_200L)));
        when(performanceQueryRepository.findProductConfirmedPurchases(
                9L, august.start(), august.end()))
                .thenReturn(List.of(
                        purchase(1L, 92L, "10826667"),
                        purchase(3L, 50L, "5000000")));

        var result = service.getSummary("selector-user", YearMonth.of(2026, 8));

        assertThat(result.activityMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(result.settlementRate()).isEqualByComparingTo("3.00");
        assertThat(result.metrics().estimatedSettlementAmount()).isEqualTo(1_284_600L);
        assertThat(result.metrics().conversionAmount()).isEqualTo(42_820_000L);
        assertThat(result.metrics().conversionCount()).isEqualTo(386L);
        assertThat(result.metrics().clickCount()).isEqualTo(12_840L);
        assertThat(result.metrics().conversionRate()).isEqualByComparingTo("3.01");
        assertThat(result.previousMonthMetrics().conversionAmount()).isEqualTo(30_000_000L);
        assertThat(result.trends()).hasSize(21);
        assertThat(result.trends().getFirst().clickCount()).isEqualTo(100L);
        assertThat(result.trends().getFirst().conversionAmount()).isEqualTo(300_000L);
        assertThat(result.topProducts()).extracting(product -> product.productId())
                .containsExactly(1L, 3L, 2L);
        assertThat(result.topProducts().getFirst().estimatedSettlementAmount())
                .isEqualTo(324_800L);
        assertThat(result.topProducts().get(1).clickCount()).isZero();
    }

    @Test
    void defaultsToCurrentMonthAndRejectsFutureMonth() {
        Selectors selectors = selector();
        Application application = application();
        when(selectorAccessService.requireCurrent("selector-user")).thenReturn(selectors);
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(application));
        when(commissionRateCalculator.calculate(SnsPlatform.INSTAGRAM, 20_000L))
                .thenReturn(new BigDecimal("3.00"));
        stubMetrics(YearMonth.of(2026, 8), 0L, 0L, 0L);
        stubMetrics(YearMonth.of(2026, 7), 0L, 0L, 0L);
        Period august = period(YearMonth.of(2026, 8));
        when(performanceQueryRepository.findDailyProductClicks(
                9L, august.start(), august.end())).thenReturn(List.of());
        when(performanceQueryRepository.findDailyConfirmedPurchases(
                9L, august.start(), august.end())).thenReturn(List.of());
        when(performanceQueryRepository.findProductClicks(
                9L, august.start(), august.end())).thenReturn(List.of());
        when(performanceQueryRepository.findProductConfirmedPurchases(
                9L, august.start(), august.end())).thenReturn(List.of());

        assertThat(service.getSummary("selector-user", null).activityMonth())
                .isEqualTo(YearMonth.of(2026, 8));
        assertThatThrownBy(() -> service.getSummary(
                "selector-user", YearMonth.of(2026, 9)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("현재 월 이후");
    }

    private void stubMetrics(
            YearMonth month, long clickCount, long amount, long conversionCount) {
        Period period = period(month);
        when(performanceQueryRepository.countProductClicks(
                9L, period.start(), period.end())).thenReturn(clickCount);
        when(performanceQueryRepository.summarizeConfirmedPurchases(
                9L, period.start(), period.end()))
                .thenReturn(new PurchaseSummary(new BigDecimal(amount), conversionCount));
    }

    private Selectors selector() {
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(9L);
        when(selectors.getApplicationId()).thenReturn(5L);
        return selectors;
    }

    private Application application() {
        Application application = mock(Application.class);
        when(application.getSnsCode()).thenReturn(SnsPlatform.INSTAGRAM);
        when(application.getFollowerCount()).thenReturn(20_000L);
        return application;
    }

    private ProductClick click(Long productId, long clickCount) {
        return new ProductClick(
                productId, "P-" + productId, "상품 " + productId,
                "브랜드", "https://example.com/" + productId + ".jpg", clickCount);
    }

    private ProductPurchase purchase(Long productId, long count, String amount) {
        return new ProductPurchase(
                productId, "P-" + productId, "상품 " + productId,
                "브랜드", "https://example.com/" + productId + ".jpg",
                count, new BigDecimal(amount));
    }

    private Period period(YearMonth month) {
        return new Period(
                month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay());
    }

    private record Period(LocalDateTime start, LocalDateTime end) {
    }
}
