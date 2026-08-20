package com.fuma.hiselectors.campaign.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.campaign.repository.CampaignParticipantProjection;

public record CampaignParticipantResponse(Long selectorId, String nickname, SnsPlatform platform,
                                          String accountId, Long followerCount) {
    public static CampaignParticipantResponse from(CampaignParticipantProjection projection) {
        return new CampaignParticipantResponse(projection.getSelectorId(), projection.getNickname(),
                projection.getPlatform() == null ? null : SnsPlatform.valueOf(projection.getPlatform()),
                projection.getAccountId(), projection.getFollowerCount());
    }
}
