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
@Table(name = "product_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductGroup extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_group_id") private Long id;
    @Column(name = "selectors_id", nullable = false) private Long selectorsId;
    @Column(name = "campaign_id", nullable = false, updatable = false) private Long campaignId;
    @Column(name = "is_deleted", nullable = false) private boolean isDeleted;

    public ProductGroup(Long selectorsId, Long campaignId) {
        this(selectorsId, campaignId, false);
    }

    public ProductGroup(Long selectorsId, Long campaignId, boolean isDeleted) {
        this.selectorsId = selectorsId;
        this.campaignId = campaignId;
        this.isDeleted = isDeleted;
    }
}
