package com.fuma.hiselectors.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GenerationServiceTest {

    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final GenerationService service = new GenerationService(
            generationRepository,
            Clock.fixed(Instant.parse("2026-08-20T15:30:00Z"), ZoneId.of("Asia/Seoul")));

    @Test
    void getsGenerationWhoseActivityPeriodContainsNow() {
        Generation generation = mock(Generation.class);
        when(generationRepository
                .findFirstByActivityStartDateLessThanEqualAndActivityEndDateGreaterThanEqualOrderByActivityStartDateAsc(
                        LocalDateTime.of(2026, 8, 21, 0, 30),
                        LocalDateTime.of(2026, 8, 21, 0, 30)))
                .thenReturn(Optional.of(generation));

        assertThat(service.getCurrentActivity()).isSameAs(generation);
    }
}
