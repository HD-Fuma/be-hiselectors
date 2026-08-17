package com.fuma.hiselectors.campaign;

import static org.mockito.Mockito.verify;

import com.fuma.hiselectors.campaign.scheduler.CampaignStatusSyncScheduler;
import com.fuma.hiselectors.campaign.service.CampaignStatusSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CampaignStatusSyncSchedulerTest {
    @Test
    void scheduled_entry_point_delegates_to_status_sync_service() {
        CampaignStatusSyncService service = Mockito.mock(CampaignStatusSyncService.class);
        new CampaignStatusSyncScheduler(service).synchronizeDaily();
        verify(service).syncStatuses();
    }
}
