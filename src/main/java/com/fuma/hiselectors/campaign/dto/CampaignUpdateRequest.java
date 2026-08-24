package com.fuma.hiselectors.campaign.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CampaignUpdateRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String title,
        @Size(max = 2000) @Pattern(regexp = ".*\\S.*") String description,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 400) @Pattern(regexp = ".*\\S.*") String thumbnailUrl,
        List<Long> productIds,
        @Schema(description = "true면 기존 썸네일을 제거한다. 생략하면 유지하며 thumbnailUrl보다 우선한다.")
        Boolean removeThumbnail) {

    public CampaignUpdateRequest(String title, String description, LocalDate startDate, LocalDate endDate,
                                 String thumbnailUrl, List<Long> productIds) {
        this(title, description, startDate, endDate, thumbnailUrl, productIds, null);
    }
}
