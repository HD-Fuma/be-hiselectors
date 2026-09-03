package com.fuma.hiselectors.creator.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.dto.CreatorDetailResponse;
import com.fuma.hiselectors.creator.dto.CreatorPoolCategoryDemoResponse;
import com.fuma.hiselectors.creator.dto.CreatorPoolDemoResponse;
import com.fuma.hiselectors.creator.dto.CreatorPoolResetResponse;
import com.fuma.hiselectors.creator.dto.DailyReportCandidatesResponse;
import com.fuma.hiselectors.creator.dto.InfluenceRankedCreator;
import com.fuma.hiselectors.creator.dto.TopPercentInfluenceResponse;
import com.fuma.hiselectors.creator.service.CreatorDiscoveryService;
import com.fuma.hiselectors.creator.service.CreatorInfluenceService;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

class CreatorAdminControllerTest {

    private CreatorDiscoveryService creatorDiscoveryService;
    private CreatorInfluenceService creatorInfluenceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        creatorDiscoveryService = mock(CreatorDiscoveryService.class);
        creatorInfluenceService = mock(CreatorInfluenceService.class);
        CreatorAdminController controller = new CreatorAdminController(
                creatorDiscoveryService, creatorInfluenceService);
        ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.addAdvice(new MethodValidationInterceptor(
                Validation.buildDefaultValidatorFactory().getValidator()));
        mockMvc = MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void 팔로워_범위를_목록조회에_전달한다() throws Exception {
        mockMvc.perform(get("/api/admin/creators")
                        .param("minFollower", "5000")
                        .param("maxFollower", "100000"))
                .andExpect(status().isOk());

        verify(creatorDiscoveryService).search(
                null, null, null, 5_000L, 100_000L,
                null, null, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "followerCount")));
    }

    @Test
    void 팔로워_범위가_음수면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/creators")
                        .param("maxFollower", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(creatorDiscoveryService);
    }

    @Test
    void 최소_팔로워가_최대값보다_크면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/admin/creators")
                        .param("minFollower", "100001")
                        .param("maxFollower", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(creatorDiscoveryService);
    }

    @Test
    void 확인된_크리에이터_풀_초기화를_실행한다() throws Exception {
        when(creatorDiscoveryService.resetPool("DELETE_CREATOR_POOL", "admin"))
                .thenReturn(new CreatorPoolResetResponse(598));

        mockMvc.perform(delete("/api/admin/creators")
                        .param("confirmation", "DELETE_CREATOR_POOL")
                        .principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.softDeletedCount").value(598));

        verify(creatorDiscoveryService).resetPool("DELETE_CREATOR_POOL", "admin");
    }

    @Test
    void 데모용_크리에이터_풀을_준비한다() throws Exception {
        when(creatorDiscoveryService.prepareDemo("admin"))
                .thenReturn(new CreatorPoolDemoResponse(72));

        mockMvc.perform(post("/api/admin/creators/demo").principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredCount").value(72));

        verify(creatorDiscoveryService).prepareDemo("admin");
    }

    @Test
    void FAST_모드_카테고리_데모_발굴을_실행한다() throws Exception {
        when(creatorDiscoveryService.prepareCategoryDemo(4L, "admin"))
                .thenReturn(new CreatorPoolCategoryDemoResponse(30, List.of(11L, 12L)));

        mockMvc.perform(post("/api/admin/creators/demo/categories/4").principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restoredCount").value(30))
                .andExpect(jsonPath("$.data.restoredCreatorIds[0]").value(11));

        verify(creatorDiscoveryService).prepareCategoryDemo(4L, "admin");
    }

    @Test
    void 크리에이터_기본_상세정보를_조회한다() throws Exception {
        CreatorDetailResponse response = new CreatorDetailResponse(
                113L, "YOUTUBE", "UC113", "다예다",
                "https://yt.example/profile.jpg",
                100_000L, new BigDecimal("4.25"),
                LocalDateTime.of(2026, 8, 12, 20, 0), "BEAUTY",
                List.of(new CategoryShare("BEAUTY", new BigDecimal("1.00"))),
                1, "공식(설명)", "imdayeda", new BigDecimal("0.95"),
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 13, 11, 0));
        when(creatorDiscoveryService.findDetail(113L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/creators/113"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(113))
                .andExpect(jsonPath("$.data.snsCode").value("YOUTUBE"))
                .andExpect(jsonPath("$.data.creatorName").value("다예다"))
                .andExpect(jsonPath("$.data.profileImageUrl")
                        .value("https://yt.example/profile.jpg"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.category").value("BEAUTY"))
                .andExpect(jsonPath("$.data.categoryShares[0].categoryCode")
                        .value("BEAUTY"))
                .andExpect(jsonPath("$.data.brandScore").value(1))
                .andExpect(jsonPath("$.data.igHandle").value("imdayeda"));

        verify(creatorDiscoveryService).findDetail(113L);
    }

    @Test
    void 존재하지_않는_크리에이터_상세조회는_404를_반환한다() throws Exception {
        when(creatorDiscoveryService.findDetail(999L))
                .thenThrow(new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        mockMvc.perform(get("/api/admin/creators/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREATOR_NOT_FOUND"));
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
                .andExpect(jsonPath("$.data.dailyTargetCount").value(8))
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
