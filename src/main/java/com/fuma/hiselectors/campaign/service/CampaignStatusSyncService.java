package com.fuma.hiselectors.campaign.service;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignStatusSyncService {
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private final CampaignRepository campaignRepository;
    private final Clock clock;

    @Transactional
    public void syncStatuses() {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL_ZONE));
        campaignRepository.findAllByIsDeletedFalseOrderByIdDesc().forEach(campaign ->
                campaign.synchronizeStatus(deriveStatus(campaign, today)));
    }

    private CampaignStatus deriveStatus(Campaign campaign, LocalDate today) {
        if (today.isBefore(campaign.getStartDate())) return CampaignStatus.SCHEDULED;
        if (today.isAfter(campaign.getEndDate())) return CampaignStatus.ENDED;
        return CampaignStatus.ACTIVE;
    }
}
