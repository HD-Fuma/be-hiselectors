package com.fuma.hiselectors.campaign.dto;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import java.time.LocalDate;
import java.util.List;

public record CampaignListResponse(Long id, String title, String description, LocalDate startDate,
                                   LocalDate endDate, String thumbnailUrl, CampaignStatus status,
                                   List<String> brands) {
    public static CampaignListResponse of(Campaign campaign, List<CampaignProduct> products,
                                          CampaignStatus status) {
        List<String> brands = products.stream().map(link -> link.getProduct().getBrandName())
                .filter(brand -> brand != null && !brand.isBlank()).map(String::trim).distinct().toList();
        return new CampaignListResponse(campaign.getId(), campaign.getTitle(), campaign.getDescription(),
                campaign.getStartDate(), campaign.getEndDate(), campaign.getThumbnailUrl(), status, brands);
    }
}
