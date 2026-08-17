package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ApplicationCreateRequest(

        @NotNull(message = "SNS 코드는 필수입니다.")
        SnsPlatform snsCode,

        @NotBlank(message = "SNS 계정 ID는 필수입니다.")
        @Size(max = 200)
        String snsAccountId,

        @PositiveOrZero(message = "팔로워 수는 0 이상이어야 합니다.")
        Long followerCount,

        LocalDateTime lastContentAt,

        @PositiveOrZero(message = "ER 지수는 0 이상이어야 합니다.")
        @Digits(integer = 3, fraction = 2, message = "ER 지수 형식이 올바르지 않습니다.")
        BigDecimal engagementRate,

        @AssertTrue(message = "개인정보 열람 동의는 필수입니다.")
        boolean privacyAgreed,

        @AssertTrue(message = "카카오톡 안내 메시지 수신 동의는 필수입니다.")
        boolean alarmAgreed
) {
}
