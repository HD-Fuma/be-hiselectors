package com.fuma.hiselectors.campaign.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Campaign extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campaign_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_status_code", nullable = false, length = 20)
    private CampaignStatus status;

    @Column(length = 100)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "thumbnail_url", length = 400)
    private String thumbnailUrl;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Builder
    private Campaign(String title, String description, LocalDate startDate, LocalDate endDate,
                     String thumbnailUrl, CampaignStatus status) {
        this.title = title;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.thumbnailUrl = thumbnailUrl;
        this.status = status;
    }

    public void update(String title, String description, LocalDate startDate, LocalDate endDate,
                       String thumbnailUrl, CampaignStatus status) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
        this.status = status;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
