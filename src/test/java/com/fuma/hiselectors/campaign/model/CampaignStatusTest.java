package com.fuma.hiselectors.campaign.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CampaignStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Test
    void derives_status_from_campaign_dates() {
        assertThat(CampaignStatus.from(TODAY.plusDays(1), TODAY.plusDays(2), TODAY))
                .isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(CampaignStatus.from(TODAY, TODAY, TODAY)).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(CampaignStatus.from(TODAY.minusDays(2), TODAY.minusDays(1), TODAY))
                .isEqualTo(CampaignStatus.ENDED);
    }
}
