package com.fuma.hiselectors.content.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.content.dto.ContentCollectionBatchResponse;
import com.fuma.hiselectors.content.service.ContentCollectionBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ContentCollectionAdminControllerTest {

    private ContentCollectionBatchService batchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        batchService = mock(ContentCollectionBatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContentCollectionAdminController(batchService))
                .setControllerAdvice(new ApiResultAdvice())
                .build();
    }

    @Test
    void postCollectionRunDelegatesOnceAndReturnsBatchResponse() throws Exception {
        ContentCollectionBatchResponse response = new ContentCollectionBatchResponse(
                10L, "1기", 3, 2, 1, 7);
        when(batchService.run()).thenReturn(response);

        mockMvc.perform(post("/api/admin/content-collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generationId").value(10))
                .andExpect(jsonPath("$.data.generationName").value("1기"))
                .andExpect(jsonPath("$.data.targetAccountCount").value(3))
                .andExpect(jsonPath("$.data.succeededAccountCount").value(2))
                .andExpect(jsonPath("$.data.failedAccountCount").value(1))
                .andExpect(jsonPath("$.data.savedContentCount").value(7));

        verify(batchService).run();
    }
}
