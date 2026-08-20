package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewContentServiceTest {

    @Mock
    private GenerationService generationService;

    @Mock
    private ContentBatchAccountRepository accountRepository;

    @InjectMocks
    private NewContentService service;

    @Test
    void createsCollectionTargetsForCurrentGeneration() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        when(generation.getId()).thenReturn(3L);
        when(generation.getStartDate()).thenReturn(generationStart);

        SelectorsSnsAccount neverCollected = account(null);
        SelectorsSnsAccount collectedBeforeGeneration =
                account(generationStart.minusDays(1));
        SelectorsSnsAccount collectedDuringGeneration =
                account(generationStart.plusHours(2));
        when(generationService.getActive()).thenReturn(generation);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(
                neverCollected, collectedBeforeGeneration, collectedDuringGeneration));

        List<NewContentService.CollectionTarget> targets = service.collectionTargets();

        assertThat(targets).extracting(NewContentService.CollectionTarget::account)
                .containsExactly(
                        neverCollected, collectedBeforeGeneration, collectedDuringGeneration);
        assertThat(targets).extracting(NewContentService.CollectionTarget::since)
                .containsExactly(
                        generationStart, generationStart, generationStart.plusHours(2));
        verify(accountRepository).findAllByGenerationId(3L);
    }

    private SelectorsSnsAccount account(LocalDateTime lastCollectedAt) {
        return SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .accountId("account")
                .lastCollectedAt(lastCollectedAt)
                .build();
    }
}
