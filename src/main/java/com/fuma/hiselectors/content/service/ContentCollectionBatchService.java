package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.dto.ContentCollectionBatchResponse;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 활성 기수의 셀렉터스 SNS 계정을 순서대로 수집한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentCollectionBatchService {

    private final GenerationService generationService;
    private final SelectorsSnsAccountRepository accountRepository;
    private final ContentCollectionService contentCollectionService;

    public ContentCollectionBatchResponse run() {
        Generation generation = generationService.getActive();
        List<SelectorsSnsAccount> targetAccounts = accountRepository
                .findAllForGenerationOrderByIdAsc(generation.getId());

        int succeededAccountCount = 0;
        int failedAccountCount = 0;
        int savedContentCount = 0;

        for (SelectorsSnsAccount account : targetAccounts) {
            try {
                savedContentCount += contentCollectionService.collectForAccount(
                        account.getId(), generation.getStartDate(), generation.getEndDate());
                succeededAccountCount++;
            } catch (RuntimeException exception) {
                failedAccountCount++;
                log.warn("콘텐츠 일괄 수집 계정 실패. accountId={}", account.getId(), exception);
            }
        }

        ContentCollectionBatchResponse response = new ContentCollectionBatchResponse(
                generation.getId(),
                generation.getGenerationName(),
                targetAccounts.size(),
                succeededAccountCount,
                failedAccountCount,
                savedContentCount);
        log.info("콘텐츠 일괄 수집 종료. {}", response);
        return response;
    }
}
