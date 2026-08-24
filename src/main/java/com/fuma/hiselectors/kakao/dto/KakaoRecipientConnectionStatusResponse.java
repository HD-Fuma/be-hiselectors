package com.fuma.hiselectors.kakao.dto;

import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;

public record KakaoRecipientConnectionStatusResponse(
        KakaoRecipientStatus status
) {
    public static KakaoRecipientConnectionStatusResponse unlinked() {
        return new KakaoRecipientConnectionStatusResponse(null);
    }

    public static KakaoRecipientConnectionStatusResponse from(UserKakaoRecipient recipient) {
        return new KakaoRecipientConnectionStatusResponse(recipient.getStatus());
    }
}
