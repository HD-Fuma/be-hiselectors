package com.fuma.hiselectors.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

class JwtAccessDeniedHandlerTest {

    @Test
    void returnsExistingApiResultEnvelopeForForbiddenRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler(JsonMapper.builder().build());

        handler.handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains(
                "\"success\":false",
                "\"code\":\"ACCESS_DENIED\"",
                "\"data\":null");
    }
}
