package com.fuma.hiselectors.selectors.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.selectors.dto.SelectorsPenaltyResponse;
import com.fuma.hiselectors.selectors.service.SelectorsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SelectorsControllerTest {

    private SelectorsService selectorsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        selectorsService = mock(SelectorsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SelectorsController(selectorsService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void returnsPenaltyPage() throws Exception {
        SelectorsPenaltyResponse response = new SelectorsPenaltyResponse(
                1L, "SEL001", "tester", 3, 2, true, List.of());
        when(selectorsService.findPenalties(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(PenaltyStatus.ACTIVE),
                org.mockito.ArgumentMatchers.eq(true),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin/selectors/penalties")
                        .param("generationId", "2")
                        .param("status", "ACTIVE")
                        .param("blacklistOnly", "true")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].selectorsId").value(1))
                .andExpect(jsonPath("$.data.content[0].totalPenaltyCount").value(3))
                .andExpect(jsonPath("$.data.content[0].activePenaltyCount").value(2))
                .andExpect(jsonPath("$.data.content[0].blacklistTarget").value(true));

        verify(selectorsService).findPenalties(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(PenaltyStatus.ACTIVE),
                org.mockito.ArgumentMatchers.eq(true),
                any(Pageable.class));
    }
}
