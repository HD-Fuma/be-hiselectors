package com.fuma.hiselectors.performance.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.DailyPerformance;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.ProductPerformance;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.SelectorPerformance;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.Summary;
import com.fuma.hiselectors.performance.service.CampaignPerformanceAdminService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CampaignPerformanceAdminControllerTest {

    private CampaignPerformanceAdminService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CampaignPerformanceAdminService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CampaignPerformanceAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsCampaignPerformanceWithOptionalPeriod() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        when(service.getPerformance(3L, startDate, endDate))
                .thenReturn(response(startDate, endDate));

        mockMvc.perform(get("/api/admin/campaigns/3/performance")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.campaignId").value(3))
                .andExpect(jsonPath("$.data.summary.confirmedSales").value(250000))
                .andExpect(jsonPath("$.data.summary.confirmedOrderCount").value(2))
                .andExpect(jsonPath("$.data.summary.canceledOrReturnedOrderCount").value(1))
                .andExpect(jsonPath("$.data.summary.canceledOrReturnedRate").value(33.33))
                .andExpect(jsonPath("$.data.daily[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.data.products[0].productCode").value("P-1"))
                .andExpect(jsonPath("$.data.selectors[0].nickname").value("셀렉터"))
                .andExpect(jsonPath("$.data.selectors[0].profileImageUrl")
                        .value("https://cdn.example.com/selector.jpg"))
                .andExpect(jsonPath("$.data.selectors[0].productCount").value(1));

        verify(service).getPerformance(3L, startDate, endDate);
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/admin/campaigns/3/performance")
                        .param("startDate", "2026/08/01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private CampaignPerformanceResponse response(LocalDate startDate, LocalDate endDate) {
        return new CampaignPerformanceResponse(
                3L,
                startDate,
                endDate,
                new Summary(
                        new BigDecimal("250000"), 2L, 3L, 1L,
                        1L, new BigDecimal("33.33")),
                List.of(new DailyPerformance(
                        startDate, new BigDecimal("250000"), 2L, 3L)),
                List.of(new ProductPerformance(
                        11L, "P-1", "상품", "브랜드", null,
                        new BigDecimal("250000"), 2L, 3L, 1L)),
                List.of(new SelectorPerformance(
                        7L, "SEL-7", "셀렉터",
                        "https://cdn.example.com/selector.jpg", new BigDecimal("250000"),
                        2L, 3L, 1L)));
    }
}
