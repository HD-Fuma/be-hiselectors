package com.fuma.hiselectors.kakao.dto;

import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;

public record KakaoRecipientAdminResponse(
        Long selectorsId,
        Long userId,
        String nickname,
        String selectorsCode,
        String email,
        String hiId,
        String recipientStatus) {

    public KakaoRecipientAdminResponse(Long selectorsId, Long userId, String nickname,
                                       String selectorsCode, String email, String hiId,
                                       KakaoRecipientStatus recipientStatus) {
        this(selectorsId, userId, nickname, selectorsCode, email, hiId,
                recipientStatus == null ? "UNLINKED" : recipientStatus.name());
    }
}
