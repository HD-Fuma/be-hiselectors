package com.fuma.hiselectors.content.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.content.dto.ContentPerformanceResponse;
import com.fuma.hiselectors.content.dto.ContentPerformanceSummaryResponse;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.service.ContentPerformanceService;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

class ContentPerformanceAdminControllerTest {

    private ContentPerformanceService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ContentPerformanceService.class);
        ContentPerformanceAdminController controller =
                new ContentPerformanceAdminController(service);
        ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.addAdvice(new MethodValidationInterceptor(
                Validation.buildDefaultValidatorFactory().getValidator()));
        mockMvc = MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsContentMetricsAndTrend() throws Exception {
        LocalDateTime recordedAt = LocalDateTime.of(2026, 8, 18, 10, 0);
        ContentPerformanceResponse item = new ContentPerformanceResponse(
                1L, 11L, "셀렉터", "1기", SnsPlatform.INSTAGRAM, "post-1",
                "https://instagram.com/p/post-1", ContentType.FEED,
                recordedAt.minusHours(1), "account", 12000L, null,
                List.of("본문"), List.of(), 300L, 30L, 3L,
                List.of(new ContentPerformanceResponse.TrendPoint(
                        recordedAt, 300L, 30L, 3L)));
        when(service.getCurrentGenerationPerformance(0, 20))
                .thenReturn(new PageImpl<>(
                        List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/content-performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].contentId").value(1))
                .andExpect(jsonPath("$.data.content[0].viewCount").value(300))
                .andExpect(jsonPath("$.data.content[0].trend[0].likeCount").value(30))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(service).getCurrentGenerationPerformance(0, 20);
    }

    @Test
    void returnsUploadAndFormatSummary() throws Exception {
        when(service.getSummary()).thenReturn(new ContentPerformanceSummaryResponse(
                59L, "2기", 13L, "1기", 10L,
                List.of(new ContentPerformanceSummaryResponse.FormatCount(
                        ContentType.SHORT_FORM, 19L))));

        mockMvc.perform(get("/api/admin/content-performance/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalContentCount").value(59))
                .andExpect(jsonPath("$.data.currentGenerationContentCount").value(13))
                .andExpect(jsonPath("$.data.previousGenerationContentCount").value(10))
                .andExpect(jsonPath("$.data.formats[0].contentType").value("SHORT_FORM"))
                .andExpect(jsonPath("$.data.formats[0].count").value(19));

        verify(service).getSummary();
    }
}
