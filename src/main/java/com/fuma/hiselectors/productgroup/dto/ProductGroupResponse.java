package com.fuma.hiselectors.productgroup.dto;

import com.fuma.hiselectors.campaign.dto.CampaignProductDisplayResponse;
import com.fuma.hiselectors.productgroup.model.ProductGroup;
import com.fuma.hiselectors.productgroup.model.ProductGroupItem;
import java.time.LocalDateTime;
import java.util.List;

public record ProductGroupResponse(
        Long id,
        Long selectorsId,
        Long campaignId,
        Short groupNo,
        String title,
        LocalDateTime createdAt,
        List<CampaignProductDisplayResponse> products
) {
    public static ProductGroupResponse of(ProductGroup group, List<ProductGroupItem> items) {
        return new ProductGroupResponse(group.getId(), group.getSelectorsId(), group.getCampaignId(),
                group.getGroupNo(), group.getTitle(), group.getCreatedAt(), items.stream()
                .map(item -> CampaignProductDisplayResponse.of(item.getProduct())).toList());
    }
}
