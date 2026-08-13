package com.fuma.hiselectors.creator.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.creator.dto.InfluenceRankedCreator;
import com.fuma.hiselectors.creator.dto.DailyReportCandidatesResponse;
import com.fuma.hiselectors.creator.dto.TopPercentInfluenceResponse;
import com.fuma.hiselectors.creator.service.CreatorDiscoveryService;
import com.fuma.hiselectors.creator.service.CreatorInfluenceService;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

class CreatorAdminControllerTest {

    private CreatorInfluenceService creatorInfluenceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CreatorDiscoveryService creatorDiscoveryService = mock(CreatorDiscoveryService.class);
        creatorInfluenceService = mock(CreatorInfluenceService.class);
        CreatorAdminController controller = new CreatorAdminController(
                creatorDiscoveryService, creatorInfluenceService);
        ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.addAdvice(new MethodValidationInterceptor(
                Validation.buildDefaultValidatorFactory().getValidator()));
        mockMvc = MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void 카테고리와_플랫폼별_영향력_상위_퍼센트를_조회한다() throws Exception {
        InfluenceRankedCreator creator = new InfluenceRankedCreator(
                1, 113L, "YOUTUBE", "UC113", "다예다", 100_000L,
                new BigDecimal("4.25"), LocalDateTime.of(2026, 8, 12, 0, 0),
                "BEAUTY", LocalDateTime.of(2026, 8, 13, 10, 0),
                new BigDecimal("90.00"), new BigDecimal("100.00"),
                new BigDecimal("80.00"), new BigDecimal("92.00"));
        TopPercentInfluenceResponse response = new TopPercentInfluenceResponse(
                "BEAUTY", "YOUTUBE", 10, 90, 19, 2, List.of(creator));
        when(creatorInfluenceService.findTopPercent("BEAUTY", "YOUTUBE", 10, 90))
                .thenReturn(response);

        mockMvc.perform(get("/api/admin/creators/top-percent")
                        .param("categoryCode", "BEAUTY")
                        .param("snsCode", "YOUTUBE")
                        .param("topPercent", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryCode").value("BEAUTY"))
                .andExpect(jsonPath("$.data.snsCode").value("YOUTUBE"))
                .andExpect(jsonPath("$.data.topPercent").value(10))
                .andExpect(jsonPath("$.data.activeWithinDays").value(90))
                .andExpect(jsonPath("$.data.totalCandidates").value(19))
                .andExpect(jsonPath("$.data.selectedCount").value(2))
                .andExpect(jsonPath("$.data.creators[0].rank").value(1))
                .andExpect(jsonPath("$.data.creators[0].creatorId").value(113))
                .andExpect(jsonPath("$.data.creators[0].influenceScore").value(92.00));

        verify(creatorInfluenceService).findTopPercent("BEAUTY", "YOUTUBE", 10, 90);
    }

    @Test
    void 카테고리별_일일_리포트_후보를_조회한다() throws Exception {
        LocalDate selectionDate = LocalDate.of(2026, 8, 13);
        DailyReportCandidatesResponse response = new DailyReportCandidatesResponse(
                selectionDate, "BEAUTY", 10, 90, 5, 100, 8, 3, List.of());
        when(creatorInfluenceService.findDailyReportCandidates(
                "BEAUTY", 10, 90, 5, selectionDate)).thenReturn(response);

        mockMvc.perform(get("/api/admin/creators/daily-report-candidates")
                        .param("categoryCode", "BEAUTY")
                        .param("selectionDate", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryCode").value("BEAUTY"))
                .andExpect(jsonPath("$.data.topPercent").value(10))
                .andExpect(jsonPath("$.data.dailyLimit").value(5))
                .andExpect(jsonPath("$.data.rankingPoolSize").value(100))
                .andExpect(jsonPath("$.data.discoveredTodayCount").value(8))
                .andExpect(jsonPath("$.data.selectedCount").value(3));
    }

    @Test
    void topPercent가_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/creators/top-percent")
                        .param("categoryCode", "BEAUTY")
                        .param("snsCode", "YOUTUBE")
                        .param("topPercent", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(creatorInfluenceService);
    }

    @Test
    void 일일_후보_조회값이_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/creators/daily-report-candidates")
                        .param("categoryCode", "BEAUTY")
                        .param("topPercent", "101")
                        .param("activeWithinDays", "0")
                        .param("dailyLimit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(creatorInfluenceService);
    }

    @Test
    void 필수_파라미터가_없거나_숫자_형식이_잘못되면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/creators/top-percent")
                        .param("snsCode", "YOUTUBE")
                        .param("topPercent", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/admin/creators/top-percent")
                        .param("categoryCode", "BEAUTY")
                        .param("snsCode", "YOUTUBE")
                        .param("topPercent", "잘못된숫자"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
