package com.fuma.hiselectors.kakao.dto;

import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;

public record KakaoRecipientConnectionResponse(
        Long userId,
        Long kakaoUserId,
        String kakaoMessageUuid,
        KakaoRecipientStatus status
) {
    public static KakaoRecipientConnectionResponse from(UserKakaoRecipient recipient) {
        return new KakaoRecipientConnectionResponse(recipient.getUserId(), recipient.getKakaoUserId(),
                recipient.getKakaoMessageUuid(), recipient.getStatus());
    }
}
