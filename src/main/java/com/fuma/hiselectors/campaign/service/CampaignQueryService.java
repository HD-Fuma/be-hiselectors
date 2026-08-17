package com.fuma.hiselectors.campaign.service;

import com.fuma.hiselectors.campaign.dto.CampaignDetailResponse;
import com.fuma.hiselectors.campaign.dto.CampaignListResponse;
import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignQueryService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final CampaignRepository campaignRepository;
    private final CampaignProductRepository campaignProductRepository;
    private final Clock clock;

    public List<CampaignListResponse> findAll() {
        List<Campaign> campaigns = campaignRepository.findAllByIsDeletedFalseOrderByIdDesc();
        if (campaigns.isEmpty()) return List.of();
        Map<Long, List<CampaignProduct>> productsByCampaignId = campaignProductRepository
                .findAllByCampaignIdInOrderByCampaignIdAscIdAsc(campaigns.stream().map(Campaign::getId).toList())
                .stream().collect(Collectors.groupingBy(link -> link.getCampaign().getId()));
        return campaigns.stream().map(campaign -> CampaignListResponse.of(campaign,
                productsByCampaignId.getOrDefault(campaign.getId(), List.of()), deriveStatus(campaign))).toList();
    }

    public CampaignDetailResponse findOne(Long campaignId) {
        Campaign campaign = campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        return CampaignDetailResponse.of(campaign,
                campaignProductRepository.findAllByCampaignId(campaignId), deriveStatus(campaign));
    }

    private CampaignStatus deriveStatus(Campaign campaign) {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL_ZONE));
        if (today.isBefore(campaign.getStartDate())) return CampaignStatus.SCHEDULED;
        if (today.isAfter(campaign.getEndDate())) return CampaignStatus.ENDED;
        return CampaignStatus.ACTIVE;
    }
}
