package com.fuma.hiselectors.selectors.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.selectors.dto.SelectorSnsEnrichmentResponse;
import com.fuma.hiselectors.selectors.service.SelectorSnsEnrichmentService;
import com.fuma.hiselectors.selectors.service.SelectorsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SelectorSnsEnrichmentAdminControllerTest {

    private SelectorSnsEnrichmentService enrichmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        enrichmentService = mock(SelectorSnsEnrichmentService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SelectorSnsEnrichmentAdminController(enrichmentService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void enrichesOneSelector() throws Exception {
        when(enrichmentService.enrich(30L, false)).thenReturn(new SelectorSnsEnrichmentResponse(
                30L, "https://cdn.example.com/mama.jpg", true, "FOOD", true, null, null));

        mockMvc.perform(post("/api/admin/selectors/30/sns-enrichment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectorsId").value(30))
                .andExpect(jsonPath("$.data.profileImageUpdated").value(true))
                .andExpect(jsonPath("$.data.category").value("FOOD"));

        verify(enrichmentService).enrich(30L, false);
    }

    @Test
    void enrichesMissingBatch() throws Exception {
        when(enrichmentService.enrichMissing(true, 10)).thenReturn(
                new SelectorSnsEnrichmentResponse.Batch(1, 1, 1, 0, List.of(
                        new SelectorSnsEnrichmentResponse(
                                30L, "https://cdn.example.com/mama.jpg", true, "FOOD", true, null, null))));

        mockMvc.perform(post("/api/admin/selectors/sns-enrichment")
                        .param("force", "true")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(1))
                .andExpect(jsonPath("$.data.profileImageUpdatedCount").value(1))
                .andExpect(jsonPath("$.data.results[0].selectorsId").value(30));

        verify(enrichmentService).enrichMissing(true, 10);
    }

    @Test
    void postSnsEnrichmentIsNotCapturedBySelectorDetailGet() throws Exception {
        SelectorsService selectorsService = mock(SelectorsService.class);
        when(enrichmentService.enrichMissing(false, 20)).thenReturn(
                new SelectorSnsEnrichmentResponse.Batch(0, 0, 0, 0, List.of()));
        MockMvc combined = MockMvcBuilders
                .standaloneSetup(
                        new SelectorsController(selectorsService),
                        new SelectorSnsEnrichmentAdminController(enrichmentService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();

        combined.perform(post("/api/admin/selectors/sns-enrichment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(0));
        combined.perform(get("/api/admin/selectors/sns-enrichment"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        verify(enrichmentService).enrichMissing(false, 20);
    }
}
