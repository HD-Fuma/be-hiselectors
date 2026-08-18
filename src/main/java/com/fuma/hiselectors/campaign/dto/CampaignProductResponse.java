package com.fuma.hiselectors.campaign.dto;

import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.model.ProductStatus;
import java.math.BigDecimal;

public record CampaignProductResponse(Long id, String code, String productName, String brandName,
                                      String category, BigDecimal regularPrice, BigDecimal salePrice,
                                      ProductStatus status, String thumbnailUrl, String detailUrl) {
    public static CampaignProductResponse of(Product product) {
        return new CampaignProductResponse(product.getId(), product.getProductCode(),
                product.getProductName(), product.getBrandName(), product.getCategory(),
                product.getRegularPrice(), product.getSalePrice(), product.getStatus(),
                product.getThumbnailUrl(), product.getDetailUrl());
    }
}
