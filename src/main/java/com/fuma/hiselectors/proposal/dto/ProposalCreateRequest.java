package com.fuma.hiselectors.proposal.dto;

import jakarta.validation.constraints.NotNull;

/** 제안 발송 요청. creator_pool.creator_pool_id 로 대상 크리에이터를 지정한다. */
public record ProposalCreateRequest(
        @NotNull Long creatorId) {
}
