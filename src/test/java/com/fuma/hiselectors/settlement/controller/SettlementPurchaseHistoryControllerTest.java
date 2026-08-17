package com.fuma.hiselectors.settlement.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse;
import com.fuma.hiselectors.settlement.service.SettlementPurchaseHistoryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SettlementPurchaseHistoryControllerTest {

    private SettlementPurchaseHistoryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(SettlementPurchaseHistoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SettlementPurchaseHistoryController(service))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new ApiResultAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchesPurchasesWithOptionalSettlementFilters() throws Exception {
        SettlementPurchaseHistoryResponse row = new SettlementPurchaseHistoryResponse(
                10L, 3L, "SEL-003", "selector", 20L, "buyer", "ORDER-1", "P-1", 1,
                BigDecimal.valueOf(10000), LocalDateTime.of(2026, 7, 15, 10, 0),
                LocalDateTime.of(2026, 7, 22, 0, 5), PurchaseStatus.PURCHASE_CONFIRMED);
        when(service.search(eq(3L), eq(YearMonth.of(2026, 7)), eq(false), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(new ArrayList<>(List.of(row)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/settlements/purchase-histories")
                        .param("selectorsId", "3")
                        .param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].purchaseHistoryId").value(10))
                .andExpect(jsonPath("$.data.content[0].status").value("PURCHASE_CONFIRMED"));

        verify(service).search(eq(3L), eq(YearMonth.of(2026, 7)), eq(false),
                org.mockito.ArgumentMatchers.any(Pageable.class));
    }
}
