package com.fuma.hiselectors.campaign.scheduler;

import com.fuma.hiselectors.campaign.service.CampaignStatusSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignStatusSyncScheduler {
    private final CampaignStatusSyncService campaignStatusSyncService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void synchronizeDaily() { campaignStatusSyncService.syncStatuses(); }
}
