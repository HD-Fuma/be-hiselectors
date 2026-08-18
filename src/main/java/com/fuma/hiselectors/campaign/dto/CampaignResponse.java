package com.fuma.hiselectors.campaign.dto;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.product.dto.ProductSearchResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CampaignResponse(Long id, CampaignStatus status, String title, String description,
                               LocalDate startDate, LocalDate endDate, String thumbnailUrl,
                               List<Long> productIds, List<ProductSearchResponse> products,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static CampaignResponse of(Campaign campaign, List<CampaignProduct> products,
                                      CampaignStatus status) {
        return new CampaignResponse(campaign.getId(), status, campaign.getTitle(),
                campaign.getDescription(), campaign.getStartDate(), campaign.getEndDate(),
                campaign.getThumbnailUrl(), products.stream().map(p -> p.getProduct().getId()).toList(),
                products.stream().map(p -> ProductSearchResponse.from(p.getProduct())).toList(),
                campaign.getCreatedAt(), campaign.getUpdatedAt());
    }
}
