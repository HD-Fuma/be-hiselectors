package com.fuma.hiselectors.purchase.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.purchase.dto.PurchaseResponse;
import com.fuma.hiselectors.purchase.model.PurchaseProcessingResult;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.service.PurchaseService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PurchaseControllerTest {

    private PurchaseService purchaseService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        purchaseService = org.mockito.Mockito.mock(PurchaseService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PurchaseController(purchaseService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void createsPurchaseThroughPublicApi() throws Exception {
        when(purchaseService.purchase(any())).thenReturn(new PurchaseResponse(
                123L, "ORDER-1", PurchaseStatus.PURCHASED, 2,
                new BigDecimal("10000"), new BigDecimal("8000"),
                new BigDecimal("4000"), new BigDecimal("16000"),
                LocalDateTime.of(2026, 8, 11, 10, 0), PurchaseProcessingResult.CREATED));

        String body = """
                {
                  "buyerUserId": 1,
                  "selectorsCode": "SELECTOR-1",
                  "productCode": "PRODUCT-1",
                  "quantity": 2
                }
                """;

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PURCHASED"))
                .andExpect(jsonPath("$.data.regularUnitPrice").value(10000))
                .andExpect(jsonPath("$.data.saleUnitPrice").value(8000))
                .andExpect(jsonPath("$.data.discountAmount").value(4000))
                .andExpect(jsonPath("$.data.paidAmount").value(16000))
                .andExpect(jsonPath("$.data.processingResult").value("CREATED"));
    }

    @Test
    void rejectsInvalidPurchaseRequest() throws Exception {
        String body = """
                {
                  "buyerUserId": 1,
                  "selectorsCode": "SELECTOR-1",
                  "productCode": "PRODUCT-1",
                  "quantity": 0
                }
                """;

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void acceptsPurchaseRequestWithoutSelectorCode() throws Exception {
        when(purchaseService.purchase(any())).thenReturn(new PurchaseResponse(
                123L, "ORDER-2", PurchaseStatus.PURCHASED, 1,
                new BigDecimal("10000"), new BigDecimal("10000"),
                BigDecimal.ZERO, new BigDecimal("10000"),
                LocalDateTime.of(2026, 8, 12, 10, 0), PurchaseProcessingResult.CREATED));

        String body = """
                {
                  "buyerUserId": 1,
                  "productCode": "PRODUCT-1",
                  "quantity": 1
                }
                """;

        mockMvc.perform(post("/api/purchases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processingResult").value("CREATED"));
    }
}
