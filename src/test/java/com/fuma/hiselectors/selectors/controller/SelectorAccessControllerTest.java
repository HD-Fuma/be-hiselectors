package com.fuma.hiselectors.selectors.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SelectorAccessControllerTest {

    private SelectorAccessService selectorAccessService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        selectorAccessService = mock(SelectorAccessService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new SelectorAccessController(selectorAccessService)).build();
    }

    @Test
    void endsAuthenticatedSelectorActivityAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/me/selector-access")
                        .principal(() -> "hi-user"))
                .andExpect(status().isNoContent());

        verify(selectorAccessService).endActivity("hi-user");
    }
}
