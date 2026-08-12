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

    @Column(name = "regular_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal regularPrice;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_status_code", nullable = false, length = 20)
    private ProductStatus status;

    @Builder
    private Product(String productCode, BigDecimal regularPrice, BigDecimal salePrice,
                    ProductStatus status) {
        this.productCode = productCode;
        this.regularPrice = regularPrice;
        this.salePrice = salePrice;
        this.status = status;
    }

    public boolean isAvailableForSale() {
        return status == ProductStatus.ON_SALE;
    }
}
