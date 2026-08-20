package com.fuma.hiselectors.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GenerationServiceTest {

    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final GenerationService service = new GenerationService(generationRepository);

    @Test
    void getsGenerationWhoseActivityPeriodContainsNow() {
        Generation generation = mock(Generation.class);
        when(generationRepository
                .findFirstByActivityStartDateLessThanEqualAndActivityEndDateGreaterThanEqualOrderByActivityStartDateAsc(
                        any(), any()))
                .thenReturn(Optional.of(generation));

        assertThat(service.getCurrentActivity()).isSameAs(generation);
    }
}
