package com.fuma.hiselectors.kakao.dto;

import com.fuma.hiselectors.kakao.model.KakaoSenderConnection;
import com.fuma.hiselectors.kakao.model.KakaoSenderConnectionStatus;

public record KakaoSenderConnectionResponse(
        Long connectionId,
        Long kakaoUserId,
        String senderName,
        KakaoSenderConnectionStatus status
) {
    public static KakaoSenderConnectionResponse from(KakaoSenderConnection connection) {
        return new KakaoSenderConnectionResponse(connection.getId(), connection.getKakaoUserId(),
                connection.getSenderName(), connection.getConnectionStatus());
    }
}
