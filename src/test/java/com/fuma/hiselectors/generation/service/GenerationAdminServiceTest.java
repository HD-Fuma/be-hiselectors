package com.fuma.hiselectors.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.dto.GenerationCreateRequest;
import com.fuma.hiselectors.generation.dto.GenerationResponse;
import com.fuma.hiselectors.generation.dto.GenerationStatusUpdateRequest;
import com.fuma.hiselectors.generation.dto.GenerationUpdateRequest;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenerationAdminServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 9, 30, 23, 59);

    @Mock
    private GenerationRepository generationRepository;

    @InjectMocks
    private GenerationAdminService generationAdminService;

    private Generation generation;

    @BeforeEach
    void setUp() {
        generation = Generation.builder()
                .generationName("1기")
                .startDate(START)
                .endDate(END)
                .status(GenerationStatus.INACTIVE)
                .build();
    }

    @Test
    void createsInactiveGeneration() {
        when(generationRepository.save(any(Generation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GenerationResponse response = generationAdminService.create(
                new GenerationCreateRequest(" 1기 ", START, END));

        assertThat(response.generationName()).isEqualTo("1기");
        assertThat(response.status()).isEqualTo(GenerationStatus.INACTIVE);
        verify(generationRepository).save(any(Generation.class));
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> generationAdminService.create(
                new GenerationCreateRequest("1기", END, START)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GENERATION_PERIOD_INVALID);

        verify(generationRepository, never()).save(any(Generation.class));
    }

    @Test
    void rejectsOverlappingActivation() {
        when(generationRepository.findById(1L)).thenReturn(Optional.of(generation));
        when(generationRepository.existsOverlapping(
                START, END, GenerationStatus.ACTIVE, 1L)).thenReturn(true);

        assertThatThrownBy(() -> generationAdminService.updateStatus(
                1L, new GenerationStatusUpdateRequest(GenerationStatus.ACTIVE)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVE_GENERATION_OVERLAPPED);

        assertThat(generation.getStatus()).isEqualTo(GenerationStatus.INACTIVE);
    }

    @Test
    void activatesWhenPeriodDoesNotOverlap() {
        when(generationRepository.findById(1L)).thenReturn(Optional.of(generation));
        when(generationRepository.existsOverlapping(
                START, END, GenerationStatus.ACTIVE, 1L)).thenReturn(false);

        GenerationResponse response = generationAdminService.updateStatus(
                1L, new GenerationStatusUpdateRequest(GenerationStatus.ACTIVE));

        assertThat(response.status()).isEqualTo(GenerationStatus.ACTIVE);
    }

    @Test
    void updatesOnlyRequestedFields() {
        LocalDateTime changedEnd = END.plusDays(7);
        when(generationRepository.findById(1L)).thenReturn(Optional.of(generation));

        GenerationResponse response = generationAdminService.update(
                1L, new GenerationUpdateRequest(" 2기 ", null, changedEnd));

        assertThat(response.generationName()).isEqualTo("2기");
        assertThat(response.startDate()).isEqualTo(START);
        assertThat(response.endDate()).isEqualTo(changedEnd);
    }
}
