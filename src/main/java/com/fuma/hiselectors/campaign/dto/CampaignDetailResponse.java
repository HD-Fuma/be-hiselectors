package com.fuma.hiselectors.campaign.dto;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import java.time.LocalDate;
import java.util.List;

public record CampaignDetailResponse(Long id, String title, String description, LocalDate startDate,
                                     LocalDate endDate, String thumbnailUrl, CampaignStatus status,
                                     List<CampaignProductDisplayResponse> products) {
    public static CampaignDetailResponse of(Campaign campaign, List<CampaignProduct> products,
                                            CampaignStatus status) {
        return new CampaignDetailResponse(campaign.getId(), campaign.getTitle(), campaign.getDescription(),
                campaign.getStartDate(), campaign.getEndDate(), campaign.getThumbnailUrl(), status,
                products.stream().map(link -> CampaignProductDisplayResponse.of(link.getProduct())).toList());
    }
}
