package com.fuma.hiselectors.campaign.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CampaignUpdateRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*") String title,
        @Size(max = 2000) @Pattern(regexp = ".*\\S.*") String description,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 400) String thumbnailUrl,
        List<Long> productIds) {
}
