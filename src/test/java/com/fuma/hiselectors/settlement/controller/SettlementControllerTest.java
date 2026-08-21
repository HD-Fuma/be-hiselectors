package com.fuma.hiselectors.settlement.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.dto.SettlementHistoryListResponse;
import com.fuma.hiselectors.settlement.dto.SettlementProvisionalEstimate;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.service.SettlementEstimateService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettlementControllerTest {

    private SettlementEstimateService settlementEstimateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settlementEstimateService = mock(SettlementEstimateService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SettlementController(settlementEstimateService))
                .setControllerAdvice(new ApiResultAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsMonthlyEstimateForAuthenticatedSelectors() throws Exception {
        when(settlementEstimateService.getEstimate("selector-user", YearMonth.of(2026, 7)))
                .thenReturn(response());

        mockMvc.perform(get("/api/settlements/estimates")
                        .param("activityMonth", "2026-07")
                        .principal(() -> "selector-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activityMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.settlementMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.paymentMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.settlementRate").value(3.00))
                .andExpect(jsonPath("$.data.settlementAmount").value(300))
                .andExpect(jsonPath("$.data.provisionalEstimate.purchaseCount").value(3))
                .andExpect(jsonPath("$.data.provisionalEstimate.salesAmount").value(12000))
                .andExpect(jsonPath("$.data.provisionalEstimate.settlementAmount").value(360));

        verify(settlementEstimateService)
                .getEstimate("selector-user", YearMonth.of(2026, 7));
    }

    @Test
    void getsYearlyHistoriesForAuthenticatedSelectors() throws Exception {
        when(settlementEstimateService.getHistories("selector-user", 2026))
                .thenReturn(new SettlementHistoryListResponse(2026, List.of(2026, 2025),
                        List.of(response())));

        mockMvc.perform(get("/api/settlements/estimates/histories")
                        .param("year", "2026")
                        .principal(() -> "selector-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedYear").value(2026))
                .andExpect(jsonPath("$.data.availableYears[0]").value(2026))
                .andExpect(jsonPath("$.data.histories[0].activityMonth").value("2026-07"));

        verify(settlementEstimateService).getHistories("selector-user", 2026);
    }

    @Test
    void leavesTheDefaultHistoryYearToTheService() throws Exception {
        when(settlementEstimateService.getHistories("selector-user", null))
                .thenReturn(new SettlementHistoryListResponse(2026, List.of(), List.of()));

        mockMvc.perform(get("/api/settlements/estimates/histories")
                        .principal(() -> "selector-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedYear").value(2026));

        verify(settlementEstimateService).getHistories("selector-user", null);
    }

    private SettlementEstimateResponse response() {
        return new SettlementEstimateResponse(
                1L,
                2L,
                "SELECTORS-1",
                "셀렉터스",
                YearMonth.of(2026, 7),
                YearMonth.of(2026, 8),
                YearMonth.of(2026, 9),
                2L,
                10_000L,
                new BigDecimal("3.00"),
                300L,
                new SettlementProvisionalEstimate(
                        3L,
                        12_000L,
                        360L,
                        LocalDateTime.of(2026, 8, 10, 12, 0)),
                SettlementStatus.CALCULATING,
                LocalDateTime.of(2026, 8, 10, 3, 0),
                LocalDateTime.of(2026, 8, 10, 3, 0));
    }
}
