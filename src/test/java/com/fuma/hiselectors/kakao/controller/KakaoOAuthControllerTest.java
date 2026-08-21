package com.fuma.hiselectors.kakao.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.kakao.dto.KakaoRecipientConnectionStatusResponse;
import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.kakao.service.KakaoOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KakaoOAuthControllerTest {

    private KakaoOAuthService oauthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        oauthService = mock(KakaoOAuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new KakaoOAuthController(oauthService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsRecipientStatusWithoutKakaoIdentifiers() throws Exception {
        when(oauthService.getRecipientStatus("hiuser1"))
                .thenReturn(new KakaoRecipientConnectionStatusResponse(KakaoRecipientStatus.READY));

        mockMvc.perform(get("/api/kakao/oauth/status").principal(() -> "hiuser1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.kakaoUserId").doesNotExist())
                .andExpect(jsonPath("$.data.kakaoMessageUuid").doesNotExist());
    }

    @Test
    void returnsNullStatusWhenRecipientIsMissing() throws Exception {
        when(oauthService.getRecipientStatus("hiuser1"))
                .thenReturn(KakaoRecipientConnectionStatusResponse.unlinked());

        mockMvc.perform(get("/api/kakao/oauth/status").principal(() -> "hiuser1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(nullValue()));
    }
}
