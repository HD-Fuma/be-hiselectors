package com.fuma.hiselectors.campaign.service;

import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.media.service.CampaignThumbnailStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignThumbnailRemovalListener {

    private final CampaignThumbnailStorage storage;
    private final CampaignRepository campaignRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void remove(CampaignThumbnailRemovalRequested event) {
        try {
            if (campaignRepository.existsByThumbnailUrl(event.url())) {
                return;
            }
            storage.delete(event.url());
        } catch (RuntimeException exception) {
            log.warn("캠페인 썸네일 객체 삭제 실패: url={}", event.url(), exception);
        }
    }
}
