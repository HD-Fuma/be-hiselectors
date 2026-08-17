package com.fuma.hiselectors.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.campaign.service.CampaignStatusSyncService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CampaignStatusSyncServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Test
    void synchronizes_only_non_deleted_campaign_statuses_using_seoul_date() {
        Campaign scheduled = campaign(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21));
        Campaign active = campaign(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19));
        Campaign ended = campaign(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18));
        given(campaignRepository.findAllByIsDeletedFalseOrderByIdDesc())
                .willReturn(java.util.List.of(scheduled, active, ended));
        CampaignStatusSyncService service = new CampaignStatusSyncService(campaignRepository,
                Clock.fixed(Instant.parse("2026-08-18T15:00:00Z"), ZoneId.of("UTC")));

        service.syncStatuses();

        assertThat(scheduled.getStatus()).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(active.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(ended.getStatus()).isEqualTo(CampaignStatus.ENDED);
    }

    private Campaign campaign(LocalDate startDate, LocalDate endDate) {
        return Campaign.builder().title("캠페인").startDate(startDate).endDate(endDate)
                .status(CampaignStatus.SCHEDULED).build();
    }
}
