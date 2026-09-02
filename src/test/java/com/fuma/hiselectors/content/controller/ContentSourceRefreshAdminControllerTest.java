package com.fuma.hiselectors.content.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.content.dto.ContentSourceRefreshResponse;
import com.fuma.hiselectors.content.service.ContentSourceRefreshService;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContentSourceRefreshAdminControllerTest {

    private ContentSourceRefreshService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ContentSourceRefreshService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContentSourceRefreshAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void refreshesIncompleteCurrentGenerationContents() throws Exception {
        when(service.refresh(null)).thenReturn(new ContentSourceRefreshResponse(
                1, 1, 1, 1, 0,
                List.of(new ContentSourceRefreshResponse.Item(
                        11L, 210L, "https://yt3.ggpht.com/mama.jpg", true,
                        List.of("지금 더현대서울 가야하는 이유"), true,
                        1200L, 80L, 9L, true, null))));

        mockMvc.perform(post("/api/admin/contents/source-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetCount").value(1))
                .andExpect(jsonPath("$.data.textsUpdatedCount").value(1))
                .andExpect(jsonPath("$.data.results[0].contentId").value(11))
                .andExpect(jsonPath("$.data.results[0].viewCount").value(1200));

        verify(service).refresh(null);
    }

    @Test
    void refreshesOneContent() throws Exception {
        when(service.refresh(11L)).thenReturn(new ContentSourceRefreshResponse(
                1, 0, 1, 1, 0,
                List.of(new ContentSourceRefreshResponse.Item(
                        11L, 210L, null, false,
                        List.of("제목"), true,
                        1L, 1L, 1L, true, null))));

        mockMvc.perform(post("/api/admin/contents/source-refresh")
                        .param("contentId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.results[0].contentId").value(11));

        verify(service).refresh(11L);
    }
}
