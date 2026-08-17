package com.fuma.hiselectors.productgroup.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_group_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductGroupItem extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_group_item_id") private Long id;
    @Column(name = "group_id", nullable = false) private Long groupId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "display_order", nullable = false) private Short displayOrder;
    @Column(name = "is_deleted", nullable = false) private boolean isDeleted;

    public ProductGroupItem(Long groupId, Long productId, Short displayOrder, boolean isDeleted) {
        this.groupId = groupId;
        this.productId = productId;
        this.displayOrder = displayOrder;
        this.isDeleted = isDeleted;
    }
}
