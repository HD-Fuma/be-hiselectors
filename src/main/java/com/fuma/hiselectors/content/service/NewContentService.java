package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewContentService {

    private final GenerationService generationService;
    private final ContentBatchAccountRepository accountRepository;

    List<CollectionTarget> collectionTargets() {
        Generation generation = generationService.getActive();
        return accountRepository.findAllByGenerationId(generation.getId()).stream()
                .map(account -> new CollectionTarget(
                        account, since(account, generation.getStartDate())))
                .toList();
    }

    private LocalDateTime since(
            SelectorsSnsAccount account, LocalDateTime generationStart) {
        LocalDateTime lastCollectedAt = account.getLastCollectedAt();
        return lastCollectedAt == null || lastCollectedAt.isBefore(generationStart)
                ? generationStart
                : lastCollectedAt;
    }

    record CollectionTarget(SelectorsSnsAccount account, LocalDateTime since) {
    }
}
