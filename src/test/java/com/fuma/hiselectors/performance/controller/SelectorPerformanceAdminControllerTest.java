package com.fuma.hiselectors.performance.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceResponse;
import com.fuma.hiselectors.performance.service.SelectorPerformanceAdminService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SelectorPerformanceAdminControllerTest {

    private SelectorPerformanceAdminService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SelectorPerformanceAdminService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SelectorPerformanceAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsSelectorPerformanceWithOptionalFilters() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 31);
        when(service.getSelectorPerformance("김", startDate, endDate))
                .thenReturn(List.of(new SelectorPerformanceResponse(
                        7L, "SEL0007", "김셀렉터", "ACTIVE", "5기", "4기",
                        new BigDecimal("14500000"),
                        new BigDecimal("12500000"), 8L, true,
                        "4기 활동 누적 1위 · 누적 매출 1,000만원 이상 달성")));

        mockMvc.perform(get("/api/admin/selector-performance")
                        .param("keyword", "김")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].selectorId").value(7))
                .andExpect(jsonPath("$.data[0].selectorCode").value("SEL0007"))
                .andExpect(jsonPath("$.data[0].generationName").value("5기"))
                .andExpect(jsonPath("$.data[0].excellentGenerationName").value("4기"))
                .andExpect(jsonPath("$.data[0].excellentGenerationSales").value(14500000))
                .andExpect(jsonPath("$.data[0].totalSales").value(12500000))
                .andExpect(jsonPath("$.data[0].confirmedOrderCount").value(8))
                .andExpect(jsonPath("$.data[0].isExcellent").value(true))
                .andExpect(jsonPath("$.data[0].excellentActivityType")
                        .value("4기 활동 누적 1위 · 누적 매출 1,000만원 이상 달성"));

        verify(service).getSelectorPerformance("김", startDate, endDate);
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get("/api/admin/selector-performance")
                        .param("startDate", "2026/08/01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
