package com.fuma.hiselectors.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse.MetricAverage;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse.QuantitativeMetrics;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse.UploadCadence;
import com.fuma.hiselectors.application.dto.AdminApplicationSummaryResponse;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.ContentAnalysisStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.service.ApplicationAdminService;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApplicationAdminControllerTest {

    private ApplicationAdminService applicationAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationAdminService = mock(ApplicationAdminService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationAdminController(applicationAdminService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void searchForwardsFiltersBeforePagingAndReturnsQuantitativeSummary() throws Exception {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 20, 12, 0);
        var response = new AdminApplicationSummaryResponse(
                1L, 10L, "hi-user", "김지안", "jian@example.com", "01012345678",
                2L, "2기", SnsPlatform.INSTAGRAM, "creator.handle",
                "https://www.instagram.com/creator.handle/", 1_000L, 500L, 12L,
                new BigDecimal("1.50"), ApplicationStatus.PENDING,
                MediaCollectionStatus.DONE, collectedAt.minusDays(30), collectedAt, collectedAt);
        when(applicationAdminService.search(
                eq("김지안"), eq(SnsPlatform.INSTAGRAM), eq(ApplicationStatus.PENDING),
                eq(2L), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(response), PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/admin/applications")
                        .param("keyword", "김지안")
                        .param("snsCode", "INSTAGRAM")
                        .param("status", "PENDING")
                        .param("generationId", "2")
                        .param("minimumCriteriaOnly", "true")
                        .param("page", "0")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].applicantName").value("김지안"))
                .andExpect(jsonPath("$.data.content[0].profileUrl")
                        .value("https://www.instagram.com/creator.handle/"))
                .andExpect(jsonPath("$.data.content[0].totalContentCount").value(500))
                .andExpect(jsonPath("$.data.content[0].recent90DayContentCount").value(12))
                .andExpect(jsonPath("$.data.content[0].engagementRate").value(1.5));

        verify(applicationAdminService).search(
                eq("김지안"), eq(SnsPlatform.INSTAGRAM), eq(ApplicationStatus.PENDING),
                eq(2L), eq(true), argThat(pageable -> pageable.getPageSize() == 100));
    }

    @Test
    void searchDistinguishesOmittedAndFalseMinimumCriteria() throws Exception {
        when(applicationAdminService.search(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(applicationAdminService.search(
                isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/admin/applications"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/applications")
                        .param("minimumCriteriaOnly", "false"))
                .andExpect(status().isOk());

        verify(applicationAdminService).search(
                isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class));
        verify(applicationAdminService).search(
                isNull(), isNull(), isNull(), isNull(), eq(false), any(Pageable.class));
    }

    @Test
    void returnsDetailWithNullValuesAndSampleCounts() throws Exception {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 20, 12, 0);
        var metrics = new QuantitativeMetrics(
                90, null, 0L, null,
                new UploadCadence(0, BigDecimal.ZERO, BigDecimal.ZERO, null),
                new MetricAverage(null, 0),
                new MetricAverage(null, 0),
                new MetricAverage(null, 0),
                new MetricAverage(null, 0),
                List.of(), null, null, null);
        when(applicationAdminService.findDetail(1L)).thenReturn(
                new AdminApplicationDetailResponse(
                        1L, 10L, "hi-user", "김지안", "jian@example.com", "01012345678",
                        2L, "2기", SnsPlatform.YOUTUBE, "UC123",
                        "https://www.youtube.com/channel/UC123", null,
                        ApplicationStatus.PENDING, MediaCollectionStatus.DONE,
                        ContentAnalysisStatus.DONE,
                        collectedAt.minusDays(30), collectedAt, collectedAt, metrics, List.of()));

        mockMvc.perform(get("/api/admin/applications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.analysisWindowDays").value(90))
                .andExpect(jsonPath("$.data.profileUrl")
                        .value("https://www.youtube.com/channel/UC123"))
                .andExpect(jsonPath("$.data.metrics.totalContentCount").isEmpty())
                .andExpect(jsonPath("$.data.metrics.averageViewCount.value").isEmpty())
                .andExpect(jsonPath("$.data.metrics.averageViewCount.sampleCount").value(0));
    }
}
