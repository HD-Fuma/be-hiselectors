package com.fuma.hiselectors.oauth.instagram.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.oauth.instagram.dto.InstagramVerifyResponse;
import com.fuma.hiselectors.oauth.instagram.service.InstagramOAuthService;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InstagramOAuthVerify400Test {

    private InstagramOAuthService service;
    private MockMvc mockMvc;
    private final Principal principal = () -> "hi-123";

    @BeforeEach
    void setUp() {
        service = mock(InstagramOAuthService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InstagramOAuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void blankCode_returns400_invalidInput_andNeverCallsService() throws Exception {
        mockMvc.perform(post("/api/instagram/oauth/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"state\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("code: code 는 필수입니다."));

        verify(service, never()).verifyAccountOwnership(any(), any(), any());
    }

    @Test
    void missingState_returns400_invalidInput() throws Exception {
        mockMvc.perform(post("/api/instagram/oauth/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ig-code\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("state: state 는 필수입니다."));
    }

    @Test
    void malformedJson_returns400_invalidInput() throws Exception {
        mockMvc.perform(post("/api/instagram/oauth/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void validBody_reachesService_returns200() throws Exception {
        when(service.verifyAccountOwnership(eq("ig-code"), eq("state-1"), eq("hi-123")))
                .thenReturn(InstagramVerifyResponse.of("igid", "iguser", 100L, 10L, "vtoken"));

        mockMvc.perform(post("/api/instagram/oauth/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ig-code\",\"state\":\"state-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("iguser"));
    }

    @Test
    void reusedCode_returnsActionable400() throws Exception {
        when(service.verifyAccountOwnership(eq("used-code"), eq("state-1"), eq("hi-123")))
                .thenThrow(new BusinessException(ErrorCode.INSTAGRAM_AUTH_CODE_INVALID));

        mockMvc.perform(post("/api/instagram/oauth/verify")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"used-code\",\"state\":\"state-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSTAGRAM_AUTH_CODE_INVALID"))
                .andExpect(jsonPath("$.message")
                        .value("만료되었거나 이미 사용된 인증 코드입니다. 다시 시도해 주세요."));
    }
}
