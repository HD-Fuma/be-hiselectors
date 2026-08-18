package com.fuma.hiselectors.selectors.dto;

import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "셀렉터스 SNS 계정")
public record SelectorsSnsAccountResponse(
        @Schema(description = "SNS 계정 ID") Long id,
        @Schema(description = "SNS 플랫폼", example = "INSTAGRAM") String snsCode,
        @Schema(description = "계정 ID") String accountId,
        @Schema(description = "팔로워 수") Long followerCount,
        @Schema(description = "프로필 이미지") String profileImageUrl,
        @Schema(description = "마지막 수집 시각") LocalDateTime lastCollectedAt
) {

    public static SelectorsSnsAccountResponse from(SelectorsSnsAccount account) {
        return new SelectorsSnsAccountResponse(
                account.getId(),
                account.getSnsCode() == null ? null : account.getSnsCode().name(),
                account.getAccountId(),
                account.getFollowerCount(),
                account.getProfileImageUrl(),
                account.getLastCollectedAt()
        );
    }
}
