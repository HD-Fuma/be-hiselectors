package com.fuma.hiselectors.productgroup.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import com.fuma.hiselectors.product.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_group_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_group_item_product", columnNames = {"group_id", "product_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductGroupItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_group_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private ProductGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "display_order", nullable = false)
    private Short displayOrder;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public ProductGroupItem(ProductGroup group, Product product, Short displayOrder) {
        this.group = group;
        this.product = product;
        this.displayOrder = displayOrder;
        this.deleted = false;
    }

    public void restore(short displayOrder) {
        this.displayOrder = displayOrder;
        this.deleted = false;
    }

    public void softDelete() {
        this.deleted = true;
    }
}
