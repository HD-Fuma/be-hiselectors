package com.fuma.hiselectors.campaign.model;

import java.time.LocalDate;

public enum CampaignStatus {
    SCHEDULED,
    ACTIVE,
    ENDED;

    public static CampaignStatus from(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (today.isBefore(startDate)) return SCHEDULED;
        if (today.isAfter(endDate)) return ENDED;
        return ACTIVE;
    }
}
