package com.fuma.hiselectors.content.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.content.dto.ContentDetailResponse;
import com.fuma.hiselectors.content.dto.ContentInspectionListItemResponse;
import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationResponse;
import com.fuma.hiselectors.content.dto.ContentInspectionMediaResponse;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.service.ContentDetailQueryService;
import com.fuma.hiselectors.content.service.ContentInspectionQueryService;
import com.fuma.hiselectors.content.service.ContentInspectionConfirmationService;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

class ContentInspectionAdminControllerTest {

    private ContentInspectionQueryService service;
    private ContentDetailQueryService detailService;
    private ContentInspectionConfirmationService confirmationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ContentInspectionQueryService.class);
        detailService = mock(ContentDetailQueryService.class);
        confirmationService = mock(ContentInspectionConfirmationService.class);
        ContentInspectionAdminController controller =
                new ContentInspectionAdminController(service, detailService, confirmationService);
        ProxyFactory proxyFactory = new ProxyFactory(controller);
        proxyFactory.addAdvice(new MethodValidationInterceptor(
                Validation.buildDefaultValidatorFactory().getValidator()));
        mockMvc = MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsWrappedPageUsingDefaultPagination() throws Exception {
        ContentInspectionListItemResponse item = response();
        when(service.getCurrentGenerationContents(0, 20))
                .thenReturn(new PageImpl<>(
                        List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/contents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].contentId").value(1))
                .andExpect(jsonPath("$.data.content[0].selectorsNickname").value("셀렉터"))
                .andExpect(jsonPath("$.data.content[0].storedAt")
                        .value("2026-08-18T09:00:00"))
                .andExpect(jsonPath("$.data.content[0].generationName").value("1기"))
                .andExpect(jsonPath("$.data.content[0].texts[0]").value("본문"))
                .andExpect(jsonPath("$.data.content[0].media[0].mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(service).getCurrentGenerationContents(0, 20);
    }

    @Test
    void acceptsPageAndSizeButDoesNotExposeClientSorting() throws Exception {
        when(service.getCurrentGenerationContents(2, 5))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mockMvc.perform(get("/api/admin/contents")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "snsCode,asc"))
                .andExpect(status().isOk());

        verify(service).getCurrentGenerationContents(2, 5);
    }

    @Test
    void returnsLatestContentDetail() throws Exception {
        when(detailService.getLatest(1L)).thenReturn(detailResponse());

        mockMvc.perform(get("/api/admin/contents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentId").value(1));

        verify(detailService).getLatest(1L);
    }

    @Test
    void returnsSelectedContentVersionDetail() throws Exception {
        when(detailService.getVersion(1L, 101L)).thenReturn(detailResponse());

        mockMvc.perform(get("/api/admin/contents/1/versions/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentId").value(1));

        verify(detailService).getVersion(1L, 101L);
    }

    @Test
    void confirmsAllPendingViolationsAndWrapsUpdatedCount() throws Exception {
        when(confirmationService.confirm(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("admin")))
                .thenReturn(new ContentInspectionConfirmationResponse(2));

        mockMvc.perform(patch("/api/admin/contents/1/versions/101/inspection")
                        .principal(() -> "admin")
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision": "REJECTED",
                                  "violations": [
                                    {"violationItemId": 21,
                                     "status": "VIOLATION_CONFIRMED"},
                                    {"violationItemId": 22, "status": "DISMISSED"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.updatedCount").value(2));
    }

    @Test
    void rejectsMissingViolationsBodyField() throws Exception {
        mockMvc.perform(patch("/api/admin/contents/1/versions/101/inspection")
                        .principal(() -> "admin")
                        .contentType("application/json")
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(confirmationService);
    }

    @Test
    void rejectsNegativePageWithClearMessage() throws Exception {
        mockMvc.perform(get("/api/admin/contents").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("page는 0 이상이어야 합니다.")));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsSizeOutsideOneToOneHundredWithClearMessage() throws Exception {
        mockMvc.perform(get("/api/admin/contents").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("size는 1 이상이어야 합니다.")));
        mockMvc.perform(get("/api/admin/contents").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("size는 100 이하여야 합니다.")));

        verifyNoInteractions(service);
    }

    private ContentInspectionListItemResponse response() {
        LocalDateTime storedAt = LocalDateTime.of(2026, 8, 18, 9, 0);
        return new ContentInspectionListItemResponse(
                1L,
                11L,
                "셀렉터",
                SnsPlatform.INSTAGRAM,
                "post-1",
                "https://instagram.com/p/post-1",
                ContentType.FEED,
                storedAt,
                101L,
                1L,
                null,
                null,
                storedAt.plusMinutes(5),
                "selectors-account",
                "https://cdn.example.com/profile.jpg",
                "1기",
                List.of("본문"),
                List.of(new ContentInspectionMediaResponse(
                        MediaType.IMAGE,
                        "https://cdn.example.com/image.jpg",
                        "https://cdn.example.com/image-thumbnail.jpg",
                        "image-1",
                        1)));
    }

    private ContentDetailResponse detailResponse() {
        return new ContentDetailResponse(
                1L,
                11L,
                SnsPlatform.INSTAGRAM,
                "post-1",
                "https://instagram.com/p/post-1",
                ContentType.FEED,
                LocalDateTime.of(2026, 8, 18, 9, 0),
                List.of(),
                null);
    }
}
