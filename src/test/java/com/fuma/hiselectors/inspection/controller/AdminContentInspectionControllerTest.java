package com.fuma.hiselectors.inspection.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService.InspectionResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminContentInspectionControllerTest {

    @Test
    void postInspectUsesDirectInspectionAndReturnsTheExistingResult() throws Exception {
        ContentInspectionExecutionService inspectionService =
                mock(ContentInspectionExecutionService.class);
        InspectionResult result = new InspectionResult(
                11L, 12L, true, ContentVersionCreationReason.EXTRACTION_CHANGE, 3);
        when(inspectionService.inspect(11L)).thenReturn(result);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminContentInspectionController(inspectionService)).build();

        mockMvc.perform(post("/api/admin/content-versions/{id}/inspect", 11L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedContentVersionId").value(11))
                .andExpect(jsonPath("$.inspectedContentVersionId").value(12))
                .andExpect(jsonPath("$.versionCreated").value(true))
                .andExpect(jsonPath("$.creationReason").value("EXTRACTION_CHANGE"))
                .andExpect(jsonPath("$.violationCount").value(3));

        verify(inspectionService).inspect(11L);
        verify(inspectionService, never()).inspectTracked(anyLong(), any());
    }
}
