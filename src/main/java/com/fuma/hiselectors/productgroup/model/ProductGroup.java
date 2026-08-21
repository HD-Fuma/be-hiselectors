package com.fuma.hiselectors.productgroup.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_group_selector_no", columnNames = {"selectors_id", "group_no"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_group_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "group_no", nullable = false)
    private Short groupNo;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public ProductGroup(Long selectorsId, Long campaignId, Short groupNo, String title) {
        this.selectorsId = selectorsId;
        this.campaignId = campaignId;
        this.groupNo = groupNo;
        this.title = title;
        this.deleted = false;
    }

    public void update(Long campaignId, String title) {
        this.campaignId = campaignId;
        this.title = title;
    }

    public void softDelete() {
        this.deleted = true;
    }
}
