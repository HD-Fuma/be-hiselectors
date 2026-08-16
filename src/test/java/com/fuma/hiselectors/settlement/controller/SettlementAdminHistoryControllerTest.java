package com.fuma.hiselectors.settlement.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.settlement.dto.SelectorSettlementDetailResponse;
import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.model.SettlementSourceCode;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.service.SettlementAdminService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettlementAdminHistoryControllerTest {

    private SettlementAdminService settlementAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settlementAdminService = mock(SettlementAdminService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SettlementAdminHistoryController(settlementAdminService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ApiResultAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsMonthlySettlementHistoriesForSelectedSelectors() throws Exception {
        when(settlementAdminService.getHistories(eq(15L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 12), 1));

        mockMvc.perform(get("/api/admin/settlements/selectors/15/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.content[0].confirmedPurchaseCount").value(2))
                .andExpect(jsonPath("$.data.content[0].status").value("PAYMENT_PENDING"));

        verify(settlementAdminService).getHistories(
                eq(15L),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 12
                        && pageable.getSort().getOrderFor("settlementMonth") != null
                        && pageable.getSort().getOrderFor("settlementMonth").isDescending()));
    }

    @Test
    void getsSettlementDetailForSelectedSelectors() throws Exception {
        when(settlementAdminService.getDetail(eq(15L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(detailResponse());

        mockMvc.perform(get("/api/admin/settlements/selectors/15/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.selectorsCode").value("SEL-0015"))
                .andExpect(jsonPath("$.data.profile.followerCount").value(76200))
                .andExpect(jsonPath("$.data.settlementSummary.cumulativePurchaseConversionCount")
                        .value(11))
                .andExpect(jsonPath("$.data.settlementSummary.nextMonthScheduledCommission")
                        .value(300))
                .andExpect(jsonPath("$.data.histories.content[0].settlementMonth").value("2026-07"));

        verify(settlementAdminService).getDetail(
                eq(15L),
                argThat(pageable -> pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 12
                        && pageable.getSort().getOrderFor("settlementMonth") != null
                        && pageable.getSort().getOrderFor("settlementMonth").isDescending()));
    }

    private SelectorSettlementDetailResponse detailResponse() {
        return new SelectorSettlementDetailResponse(
                new SelectorSettlementDetailResponse.SelectorProfile(
                        15L,
                        "SEL-0015",
                        "박도윤",
                        com.fuma.hiselectors.application.model.SnsPlatform.YOUTUBE,
                        "UC123",
                        76_200L,
                        "https://cdn.example.com/profile.jpg",
                        LocalDateTime.of(2026, 8, 15, 9, 0)),
                new SelectorSettlementDetailResponse.SettlementSummary(
                        11L,
                        1_500L,
                        2L,
                        YearMonth.of(2026, 8),
                        300L,
                        YearMonth.of(2026, 9),
                        SettlementStatus.CALCULATING),
                new PageImpl<>(List.of(response()), PageRequest.of(0, 12), 1));
    }

    private SettlementEstimateResponse response() {
        return new SettlementEstimateResponse(
                1L,
                15L,
                "SEL-0015",
                "박도윤",
                YearMonth.of(2026, 7),
                2L,
                10_000L,
                new BigDecimal("3.00"),
                300L,
                SettlementStatus.PAYMENT_PENDING,
                SettlementSourceCode.DAILY_BATCH,
                LocalDateTime.of(2026, 8, 1, 3, 0),
                LocalDateTime.of(2026, 8, 1, 3, 0));
    }
}
