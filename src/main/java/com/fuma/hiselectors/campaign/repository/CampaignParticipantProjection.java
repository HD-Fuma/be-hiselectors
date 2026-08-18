package com.fuma.hiselectors.campaign.repository;

public interface CampaignParticipantProjection {
    Long getSelectorId();
    String getNickname();
    String getPlatform();
    String getAccountId();
    Long getFollowerCount();
}
