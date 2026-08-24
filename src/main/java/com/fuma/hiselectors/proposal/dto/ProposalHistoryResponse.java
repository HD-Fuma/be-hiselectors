package com.fuma.hiselectors.proposal.dto;

import java.time.LocalDateTime;

/** 제안 이력 목록 1행. proposal_history + creator_pool + admin 조인 결과. */
public record ProposalHistoryResponse(
        Long proposalHistoryId,
        Long creatorId,
        String creatorName,
        String snsCode,
        String accountId,
        String email,
        String adminName,
        LocalDateTime createdAt) {
}
