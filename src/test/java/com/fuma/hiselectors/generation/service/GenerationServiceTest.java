package com.fuma.hiselectors.generation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.config.TimeConfig;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock
    private GenerationRepository generationRepository;

    @Test
    void findActiveGenerationAtCurrentSeoulTime() {
        Clock seoulClock = Clock.fixed(
                Instant.parse("2026-08-17T15:30:00Z"), TimeConfig.SEOUL_ZONE);
        LocalDateTime expectedNow = LocalDateTime.of(2026, 8, 18, 0, 30);
        Generation activeGeneration = Generation.builder()
                .generationName("1기")
                .startDate(expectedNow.minusDays(1))
                .endDate(expectedNow.plusDays(1))
                .status(GenerationStatus.ACTIVE)
                .build();
        when(generationRepository
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        any(LocalDateTime.class), any(LocalDateTime.class),
                        eq(GenerationStatus.ACTIVE)))
                .thenReturn(Optional.of(activeGeneration));
        GenerationService service = new GenerationService(generationRepository, seoulClock);

        service.getActive();

        verify(generationRepository)
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
                        expectedNow, expectedNow, GenerationStatus.ACTIVE);
    }
}
