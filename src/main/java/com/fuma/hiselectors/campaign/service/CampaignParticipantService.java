package com.fuma.hiselectors.campaign.service;

import com.fuma.hiselectors.campaign.dto.CampaignParticipantResponse;
import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignParticipantService {
    private final CampaignRepository campaignRepository;

    public Page<CampaignParticipantResponse> findParticipants(Long campaignId, Pageable pageable) {
        Campaign campaign = campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        LocalDateTime startAt = campaign.getStartDate().atStartOfDay();
        LocalDateTime endExclusive = campaign.getEndDate().plusDays(1).atStartOfDay();
        return campaignRepository.findParticipants(campaignId, startAt, endExclusive, pageable)
                .map(CampaignParticipantResponse::from);
    }
}
