package com.fuma.hiselectors.settlement.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.dto.SettlementRecalculationResponse;
import com.fuma.hiselectors.settlement.service.SettlementAdminService;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import com.fuma.hiselectors.settlement.service.SettlementRecalculationService;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettlementAdminControllerTest {

    private SettlementRecalculationService settlementRecalculationService;
    private SettlementPaymentService settlementPaymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settlementRecalculationService = mock(SettlementRecalculationService.class);
        settlementPaymentService = mock(SettlementPaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SettlementAdminController(
                        mock(SettlementAdminService.class), settlementRecalculationService,
                        settlementPaymentService))
                .setControllerAdvice(new ApiResultAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void recalculatesRequestedMonthForSelectedSelectors() throws Exception {
        when(settlementRecalculationService.recalculate(YearMonth.of(2026, 7), 10L, false))
                .thenReturn(new SettlementRecalculationResponse(
                        10L,
                        YearMonth.of(2026, 7),
                        YearMonth.of(2026, 7),
                        YearMonth.of(2026, 7),
                        1,
                        1,
                        0,
                        1,
                        0,
                        0,
                        0));

        mockMvc.perform(post("/api/admin/settlements/estimates/recalculate")
                        .param("month", "2026-07")
                        .param("selectorsId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectorsId").value(10))
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        verify(settlementRecalculationService).recalculate(eq(YearMonth.of(2026, 7)), eq(10L), eq(false));
    }

    @Test
    void processesPaymentForRequestedMonth() throws Exception {
        when(settlementPaymentService.process(YearMonth.of(2026, 6)))
                .thenReturn(new SettlementPaymentResponse(YearMonth.of(2026, 6), 3, 2, 1, 0, 0));

        mockMvc.perform(post("/api/admin/settlements/estimates/payments/process")
                        .param("month", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetSettlementMonth").value("2026-06"))
                .andExpect(jsonPath("$.data.settledCount").value(2))
                .andExpect(jsonPath("$.data.heldCount").value(1));

        verify(settlementPaymentService).process(YearMonth.of(2026, 6));
    }
}
