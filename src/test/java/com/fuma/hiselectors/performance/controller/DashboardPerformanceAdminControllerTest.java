package com.fuma.hiselectors.performance.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.performance.dto.DashboardSalesSettlementTrendResponse;
import com.fuma.hiselectors.performance.dto.DashboardSalesSettlementTrendResponse.Point;
import com.fuma.hiselectors.performance.service.DashboardSalesSettlementTrendService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardPerformanceAdminControllerTest {

    private DashboardSalesSettlementTrendService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DashboardSalesSettlementTrendService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardPerformanceAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsDashboardSalesSettlementTrend() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 21);
        LocalDate endDate = LocalDate.of(2026, 8, 27);
        when(service.getTrend(startDate, endDate)).thenReturn(
                new DashboardSalesSettlementTrendResponse(
                        startDate,
                        endDate,
                        List.of(new Point(
                                startDate, new BigDecimal("1000"), new BigDecimal("50")))));

        mockMvc.perform(get("/api/admin/dashboard/sales-settlement-trend")
                        .param("startDate", "2026-08-21")
                        .param("endDate", "2026-08-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value("2026-08-21"))
                .andExpect(jsonPath("$.data.points[0].salesAmount").value(1000))
                .andExpect(jsonPath("$.data.points[0].settlementAmount").value(50));

        verify(service).getTrend(startDate, endDate);
    }

    @Test
    void requiresBothDates() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/sales-settlement-trend")
                        .param("startDate", "2026-08-21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
