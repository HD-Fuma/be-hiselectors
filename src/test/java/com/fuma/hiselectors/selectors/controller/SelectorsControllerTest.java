package com.fuma.hiselectors.selectors.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.selectors.dto.SelectorsDetailResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsPenaltyResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsSnsAccountResponse;
import com.fuma.hiselectors.selectors.service.SelectorsService;
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

class SelectorsControllerTest {

    private SelectorsService selectorsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        selectorsService = mock(SelectorsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SelectorsController(selectorsService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsOneSnsAccountInDetail() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        when(selectorsService.findDetail(7L)).thenReturn(new SelectorsDetailResponse(
                7L, "SEL-2601-007", "지안글로우", "INACTIVE", "비활성",
                1L, 1L, now.minusDays(10), now.minusDays(9), true,
                now, now, List.of(),
                new SelectorsSnsAccountResponse(
                        10L, "YOUTUBE", "jianglow", 40_900L, null, now),
                3, 2, true,
                List.of(new SelectorsDetailResponse.ContentResponse(
                        20L, "YOUTUBE", "https://youtube.com/shorts/20", "실제 제목", "SHORTS",
                        now, 1_000L, 100L, 10L)),
                new SelectorsDetailResponse.PerformanceResponse(6L, 9_000L, null, 0L)));

        mockMvc.perform(get("/api/admin/selectors/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snsAccount.accountId").value("jianglow"))
                .andExpect(jsonPath("$.data.snsAccounts").doesNotExist())
                .andExpect(jsonPath("$.data.snsVerifiedAt").value("2026-08-10T12:00:00"))
                .andExpect(jsonPath("$.data.privacyAgreedAt").value("2026-08-11T12:00:00"))
                .andExpect(jsonPath("$.data.alimtalkAgreed").value(true))
                .andExpect(jsonPath("$.data.totalPenaltyCount").value(3))
                .andExpect(jsonPath("$.data.activePenaltyCount").value(2))
                .andExpect(jsonPath("$.data.blacklistTarget").value(true))
                .andExpect(jsonPath("$.data.contents[0].snsCode").value("YOUTUBE"))
                .andExpect(jsonPath("$.data.contents[0].title").value("실제 제목"))
                .andExpect(jsonPath("$.data.contents[0].viewCount").value(1000))
                .andExpect(jsonPath("$.data.performance.contentCount").value(6))
                .andExpect(jsonPath("$.data.performance.totalViewCount").value(9000))
                .andExpect(jsonPath("$.data.performance.totalLikeCount").value(nullValue()))
                .andExpect(jsonPath("$.data.performance.totalCommentCount").value(0));
    }

    @Test
    void returnsPenaltyPage() throws Exception {
        SelectorsPenaltyResponse response = new SelectorsPenaltyResponse(
                1L, "SEL001", "tester", 3, 2, true, List.of());
        when(selectorsService.findPenalties(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(PenaltyStatus.ACTIVE),
                org.mockito.ArgumentMatchers.eq(true),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/selectors/penalties")
                        .param("generationId", "2")
                        .param("status", "ACTIVE")
                        .param("blacklistOnly", "true")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].selectorsId").value(1))
                .andExpect(jsonPath("$.data.content[0].totalPenaltyCount").value(3))
                .andExpect(jsonPath("$.data.content[0].activePenaltyCount").value(2))
                .andExpect(jsonPath("$.data.content[0].blacklistTarget").value(true));

        verify(selectorsService).findPenalties(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(PenaltyStatus.ACTIVE),
                org.mockito.ArgumentMatchers.eq(true),
                any(Pageable.class));
    }
}
