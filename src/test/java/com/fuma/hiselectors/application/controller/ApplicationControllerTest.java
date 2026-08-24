package com.fuma.hiselectors.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.service.ApplicationService;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApplicationControllerTest {

    private ApplicationService applicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicationService = mock(ApplicationService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ApplicationController(applicationService))
                // ApiResultAdvice 를 함께 등록해 실제 런타임처럼 성공 응답이 봉투로 감싸지는지 검증한다
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void 성공응답은_ApiResult_봉투로_감싼다() throws Exception {
        ApplicationResponse response = new ApplicationResponse(
                1L, 10L, 3L, SnsPlatform.YOUTUBE, "UC123",
                "https://www.youtube.com/channel/UC123", 100L, 42L,
                null, null, true, LocalDateTime.now(), ApplicationStatus.PENDING,
                LocalDateTime.now());
        when(applicationService.create(eq("user1"), any())).thenReturn(response);

        String body = """
                {
                  "verificationToken": "signed-verification-token",
                  "snsCode": "YOUTUBE",
                  "snsAccountId": "UC123",
                  "followerCount": 100,
                  "contentCount": 42,
                  "privacyAgreed": true,
                  "alarmAgreed": true
                }
                """;

        mockMvc.perform(post("/api/applications")
                        .principal(() -> "user1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.profileUrl")
                        .value("https://www.youtube.com/channel/UC123"))
                .andExpect(jsonPath("$.data.contentCount").value(42))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void rejectsWhenPrivacyConsentMissing() throws Exception {
        String body = """
                {
                  "verificationToken": "signed-verification-token",
                  "snsCode": "YOUTUBE",
                  "snsAccountId": "UC123",
                  "followerCount": 100,
                  "privacyAgreed": false,
                  "alarmAgreed": true
                }
                """;

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void rejectsWhenVerificationTokenMissing() throws Exception {
        String body = """
                {
                  "snsCode": "YOUTUBE",
                  "snsAccountId": "UC123",
                  "followerCount": 100,
                  "privacyAgreed": true,
                  "alarmAgreed": true
                }
                """;

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void rejectsWhenAlarmConsentMissing() throws Exception {
        String body = """
                {
                  "verificationToken": "signed-verification-token",
                  "snsCode": "YOUTUBE",
                  "snsAccountId": "UC123",
                  "followerCount": 100,
                  "privacyAgreed": true,
                  "alarmAgreed": false
                }
                """;

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
