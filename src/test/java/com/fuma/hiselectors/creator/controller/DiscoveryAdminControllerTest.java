package com.fuma.hiselectors.creator.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.InstagramDiscoveryService;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DiscoveryAdminControllerTest {

    private InstagramDiscoveryService instagramDiscoveryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DiscoveryPipelineService discoveryPipelineService =
                mock(DiscoveryPipelineService.class);
        instagramDiscoveryService = mock(InstagramDiscoveryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DiscoveryAdminController(
                        discoveryPipelineService,
                        instagramDiscoveryService
                ))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void YouTube_크리에이터의_Instagram_발굴을_실행한다() throws Exception {
        InstagramDiscoveryResult result = new InstagramDiscoveryResult(
                10L,
                20L,
                "nike",
                true,
                291_530_362L,
                1_668L,
                new BigDecimal("0.04"),
                LocalDateTime.of(2026, 8, 12, 2, 0, 58)
        );
        when(instagramDiscoveryService.discoverFromYoutubeCreator(10L))
                .thenReturn(result);

        mockMvc.perform(post("/api/admin/discovery/creators/10/instagram"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceCreatorId").value(10))
                .andExpect(jsonPath("$.data.instagramCreatorId").value(20))
                .andExpect(jsonPath("$.data.username").value("nike"))
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(291_530_362))
                .andExpect(jsonPath("$.data.mediaCount").value(1_668))
                .andExpect(jsonPath("$.data.engagementRate").value(0.04));

        verify(instagramDiscoveryService).discoverFromYoutubeCreator(10L);
    }

    @Test
    void Instagram_사용자명이_없으면_404를_반환한다() throws Exception {
        when(instagramDiscoveryService.discoverFromYoutubeCreator(10L))
                .thenThrow(new BusinessException(ErrorCode.INSTAGRAM_HANDLE_NOT_FOUND));

        mockMvc.perform(post("/api/admin/discovery/creators/10/instagram"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INSTAGRAM_HANDLE_NOT_FOUND"));
    }
}
