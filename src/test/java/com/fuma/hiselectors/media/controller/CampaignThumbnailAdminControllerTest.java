package com.fuma.hiselectors.media.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.media.dto.CampaignThumbnailUploadResponse;
import com.fuma.hiselectors.media.service.CampaignThumbnailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

class CampaignThumbnailAdminControllerTest {

    private CampaignThumbnailService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CampaignThumbnailService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CampaignThumbnailAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void uploadsCampaignThumbnailAsMultipartData() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "campaign.webp", "image/webp", "RIFF0000WEBP".getBytes());
        when(service.upload(any(MultipartFile.class)))
                .thenReturn(new CampaignThumbnailUploadResponse(
                        "https://media.hiselectors.shop/campaigns/thumbnail.webp"));

        mockMvc.perform(multipart("/api/admin/uploads/campaign-thumbnails").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url")
                        .value("https://media.hiselectors.shop/campaigns/thumbnail.webp"));

        verify(service).upload(any(MultipartFile.class));
    }

    @Test
    void rejectsRequestWithoutFilePart() throws Exception {
        mockMvc.perform(multipart("/api/admin/uploads/campaign-thumbnails"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
