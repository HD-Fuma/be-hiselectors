package com.fuma.hiselectors.performance.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.performance.dto.PerformanceMetricsResponse;
import com.fuma.hiselectors.performance.dto.PerformanceSummaryResponse;
import com.fuma.hiselectors.performance.dto.PerformanceTrendResponse;
import com.fuma.hiselectors.performance.dto.ProductPerformanceListResponse;
import com.fuma.hiselectors.performance.dto.ProductPerformanceResponse;
import com.fuma.hiselectors.performance.service.PerformanceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PerformanceControllerTest {

    private PerformanceService performanceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        performanceService = mock(PerformanceService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PerformanceController(performanceService))
                .setControllerAdvice(new ApiResultAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsMonthlySummaryForAuthenticatedSelector() throws Exception {
        YearMonth month = YearMonth.of(2026, 8);
        PerformanceMetricsResponse metrics = new PerformanceMetricsResponse(
                1_284_600L, 42_820_000L, 386L, 12_840L, new BigDecimal("3.01"));
        when(performanceService.getSummary("selector-user", month))
                .thenReturn(new PerformanceSummaryResponse(
                        month,
                        new BigDecimal("3.00"),
                        metrics,
                        new PerformanceMetricsResponse(
                                900_000L, 30_000_000L, 300L, 10_000L,
                                new BigDecimal("3.00")),
                        List.of(new PerformanceTrendResponse(
                                LocalDate.of(2026, 8, 1), 100L, 3L, 300_000L)),
                        List.of(product())));

        mockMvc.perform(get("/api/performance/summary")
                        .param("activityMonth", "2026-08")
                        .principal(() -> "selector-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.settlementRate").value(3.00))
                .andExpect(jsonPath("$.data.metrics.estimatedSettlementAmount")
                        .value(1_284_600L))
                .andExpect(jsonPath("$.data.metrics.conversionAmount").value(42_820_000L))
                .andExpect(jsonPath("$.data.metrics.conversionCount").value(386L))
                .andExpect(jsonPath("$.data.metrics.clickCount").value(12_840L))
                .andExpect(jsonPath("$.data.metrics.conversionRate").value(3.01))
                .andExpect(jsonPath("$.data.trends[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.data.topProducts[0].productId").value(1L));

        verify(performanceService).getSummary("selector-user", month);
    }

    @Test
    void getsMonthlyProductPerformanceForAuthenticatedSelector() throws Exception {
        YearMonth month = YearMonth.of(2026, 8);
        when(performanceService.getProducts("selector-user", month))
                .thenReturn(new ProductPerformanceListResponse(
                        month, 92L, 1, List.of(product())));

        mockMvc.perform(get("/api/performance/products")
                        .param("activityMonth", "2026-08")
                        .principal(() -> "selector-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.conversionCount").value(92L))
                .andExpect(jsonPath("$.data.totalProductCount").value(1))
                .andExpect(jsonPath("$.data.products[0].clickCount").value(2_840L))
                .andExpect(jsonPath("$.data.products[0].estimatedSettlementAmount")
                        .value(324_800L));

        verify(performanceService).getProducts("selector-user", month);
    }

    private ProductPerformanceResponse product() {
        return new ProductPerformanceResponse(
                1L,
                "P-1",
                "상품 1",
                "브랜드",
                "https://example.com/product.jpg",
                2_840L,
                92L,
                10_826_667L,
                new BigDecimal("3.24"),
                324_800L);
    }
}
