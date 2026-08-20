package com.fuma.hiselectors.content.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContentBatchAdminControllerTest {

    private ContentBatchService contentBatchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        contentBatchService = mock(ContentBatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContentBatchAdminController(contentBatchService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void runsContentBatchManually() throws Exception {
        ContentBatchResult result = new ContentBatchResult(2, 5, true, true);
        when(contentBatchService.run()).thenReturn(result);

        mockMvc.perform(post("/api/admin/content-batch/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.newContentCount").value(2))
                .andExpect(jsonPath("$.data.engagementCount").value(5))
                .andExpect(jsonPath("$.data.newContentSucceeded").value(true))
                .andExpect(jsonPath("$.data.storedContentSucceeded").value(true));

        verify(contentBatchService).run();
    }
}
