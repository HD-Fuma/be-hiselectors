package com.fuma.hiselectors.application.dto;

import jakarta.validation.constraints.AssertTrue;

public record ApplicationCreateRequest(

        @AssertTrue(message = "개인정보 열람 동의는 필수입니다.")
        boolean privacyAgreed,

        @AssertTrue(message = "카카오 알림톡 수신 동의는 필수입니다.")
        boolean alarmAgreed
) {
}
