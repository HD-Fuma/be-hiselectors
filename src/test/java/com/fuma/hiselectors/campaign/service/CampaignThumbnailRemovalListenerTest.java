package com.fuma.hiselectors.campaign.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.media.service.CampaignThumbnailStorage;
import org.junit.jupiter.api.Test;

class CampaignThumbnailRemovalListenerTest {

    @Test
    void deletesThumbnailRequestedAfterCommit() {
        CampaignThumbnailStorage storage = mock(CampaignThumbnailStorage.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignThumbnailRemovalListener listener = new CampaignThumbnailRemovalListener(
                storage, campaignRepository);
        String url = "https://media.hiselectors.shop/campaigns/123e4567-e89b-12d3-a456-426614174000.png";

        listener.remove(new CampaignThumbnailRemovalRequested(url));

        verify(storage).delete(url);
    }

    @Test
    void keepsCommittedCampaignUpdateWhenStorageDeletionFails() {
        CampaignThumbnailStorage storage = mock(CampaignThumbnailStorage.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignThumbnailRemovalListener listener = new CampaignThumbnailRemovalListener(
                storage, campaignRepository);
        String url = "https://media.hiselectors.shop/campaigns/123e4567-e89b-12d3-a456-426614174000.png";
        doThrow(new IllegalStateException("S3 unavailable")).when(storage).delete(url);

        assertThatCode(() -> listener.remove(new CampaignThumbnailRemovalRequested(url)))
                .doesNotThrowAnyException();
    }

    @Test
    void keepsThumbnailObjectWhileAnotherCampaignReferencesItsUrl() {
        CampaignThumbnailStorage storage = mock(CampaignThumbnailStorage.class);
        CampaignRepository campaignRepository = mock(CampaignRepository.class);
        CampaignThumbnailRemovalListener listener = new CampaignThumbnailRemovalListener(
                storage, campaignRepository);
        String url = "https://media.hiselectors.shop/campaigns/123e4567-e89b-12d3-a456-426614174000.png";
        when(campaignRepository.existsByThumbnailUrl(url)).thenReturn(true);

        listener.remove(new CampaignThumbnailRemovalRequested(url));

        verify(storage, never()).delete(url);
    }
}
