package com.fuma.hiselectors.product.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_product_code", columnNames = "product_code")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "brand_name", length = 100)
    private String brandName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "regular_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "detail_url", length = 500)
    private String detailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Builder
    private Product(String productCode, String productName, String brandName, String category,
                    BigDecimal regularPrice, BigDecimal salePrice, ProductStatus status,
                    String thumbnailUrl, String detailUrl) {
        this.productCode = productCode;
        this.productName = productName;
        this.brandName = brandName;
        this.category = category;
        this.regularPrice = regularPrice;
        this.salePrice = salePrice;
        this.status = status;
        this.thumbnailUrl = thumbnailUrl;
        this.detailUrl = detailUrl;
    }

    public boolean isAvailableForSale() {
        return status == ProductStatus.ON_SALE;
    }
}
