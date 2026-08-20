package com.fuma.hiselectors.analytics.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "click_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "click_log_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private ViewPageType linkType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "viewer_user_id")
    private Long viewerUserId;

    public ClickLog(Long selectorsId, ViewPageType linkType, Long referenceId,
                    Long viewerUserId) {
        this.selectorsId = selectorsId;
        this.linkType = linkType;
        this.referenceId = referenceId;
        this.viewerUserId = viewerUserId;
    }
}
