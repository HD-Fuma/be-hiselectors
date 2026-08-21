package com.fuma.hiselectors.user.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.user.dto.UserMeResponse;
import com.fuma.hiselectors.user.service.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserControllerTest {

    private UserQueryService userQueryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userQueryService = mock(UserQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userQueryService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsCurrentMemberProfile() throws Exception {
        when(userQueryService.getMe("hiuser1")).thenReturn(
                new UserMeResponse("hiuser1", "홍길동", "hong@example.com", "01012345678", "Y"));

        mockMvc.perform(get("/api/users/me").principal(() -> "hiuser1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hiId").value("hiuser1"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.alimtalk").value("Y"));
    }
}
