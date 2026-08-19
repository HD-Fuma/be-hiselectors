package com.fuma.hiselectors.generation.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.generation.dto.GenerationCreateRequest;
import com.fuma.hiselectors.generation.dto.GenerationResponse;
import com.fuma.hiselectors.generation.dto.GenerationStatusUpdateRequest;
import com.fuma.hiselectors.generation.dto.GenerationUpdateRequest;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.service.GenerationAdminService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GenerationAdminControllerTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 9, 30, 23, 59);

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private GenerationAdminService generationAdminService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        generationAdminService = mock(GenerationAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new GenerationAdminController(generationAdminService))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void createsGeneration() throws Exception {
        GenerationCreateRequest request = new GenerationCreateRequest("1기", START, END);
        GenerationResponse response = new GenerationResponse(
                1L, "1기", START, END, GenerationStatus.INACTIVE);
        when(generationAdminService.create(request)).thenReturn(response);

        mockMvc.perform(post("/api/admin/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.generationName").value("1기"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        verify(generationAdminService).create(request);
    }

    @Test
    void findsAllGenerations() throws Exception {
        GenerationResponse response = new GenerationResponse(
                1L, "1기", START, END, GenerationStatus.INACTIVE);
        when(generationAdminService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/admin/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].generationName").value("1기"));

        verify(generationAdminService).findAll();
    }

    @Test
    void updatesGeneration() throws Exception {
        GenerationUpdateRequest request = new GenerationUpdateRequest("2기", null, END);
        GenerationResponse response = new GenerationResponse(
                1L, "2기", START, END, GenerationStatus.INACTIVE);
        when(generationAdminService.update(1L, request)).thenReturn(response);

        mockMvc.perform(patch("/api/admin/generations/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generationName").value("2기"));

        verify(generationAdminService).update(1L, request);
    }

    @Test
    void updatesGenerationStatus() throws Exception {
        GenerationStatusUpdateRequest request =
                new GenerationStatusUpdateRequest(GenerationStatus.ACTIVE);
        GenerationResponse response = new GenerationResponse(
                1L, "1기", START, END, GenerationStatus.ACTIVE);
        when(generationAdminService.updateStatus(1L, request)).thenReturn(response);

        mockMvc.perform(patch("/api/admin/generations/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(generationAdminService).updateStatus(1L, request);
    }

    @Test
    void rejectsMissingCreateFields() throws Exception {
        mockMvc.perform(post("/api/admin/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
