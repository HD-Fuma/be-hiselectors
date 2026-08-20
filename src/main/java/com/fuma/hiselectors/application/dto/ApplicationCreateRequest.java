package com.fuma.hiselectors.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ApplicationCreateRequest(

        @NotBlank(message = "SNS 계정 인증 토큰은 필수입니다.")
        String verificationToken,

        @AssertTrue(message = "개인정보 열람 동의는 필수입니다.")
        boolean privacyAgreed,

        @AssertTrue(message = "카카오톡 안내 메시지 수신 동의는 필수입니다.")
        boolean alarmAgreed
) {
}
