package com.fuma.hiselectors.selectors.excellence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.selectors.excellence.service.SelectorExcellenceGenerationWorker.SelectionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectorExcellenceSelectionServiceTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 17, 0, 0);

    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final SelectorExcellenceGenerationWorker worker =
            mock(SelectorExcellenceGenerationWorker.class);
    private final SelectorExcellenceSelectionService service =
            new SelectorExcellenceSelectionService(
                    generationRepository,
                    worker,
                    Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC),
                    7);

    @Test
    void calculatesCalendarDayGraceCutoffAndContinuesAfterOneGenerationFails() {
        LocalDateTime cutoffExclusive = LocalDateTime.of(2026, 8, 11, 0, 0);
        when(generationRepository.findExcellenceSelectionCandidateIds(cutoffExclusive))
                .thenReturn(List.of(1L, 2L, 3L));
        when(worker.select(1L, AS_OF, 7)).thenReturn(new SelectionResult(true, 5));
        when(worker.select(2L, AS_OF, 7)).thenThrow(new IllegalStateException("failed"));
        when(worker.select(3L, AS_OF, 7)).thenReturn(new SelectionResult(false, 0));

        SelectorExcellenceSelectionService.BatchResult result =
                service.selectEligibleGenerations();

        assertThat(result.candidateGenerationCount()).isEqualTo(3);
        assertThat(result.processedGenerationCount()).isEqualTo(1);
        assertThat(result.skippedGenerationCount()).isEqualTo(1);
        assertThat(result.selectionCount()).isEqualTo(5);
        assertThat(result.failedGenerationCount()).isEqualTo(1);
        verify(worker).select(1L, AS_OF, 7);
        verify(worker).select(2L, AS_OF, 7);
        verify(worker).select(3L, AS_OF, 7);
    }
}
