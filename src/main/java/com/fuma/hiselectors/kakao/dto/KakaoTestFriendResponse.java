package com.fuma.hiselectors.kakao.dto;

public record KakaoTestFriendResponse(
        Long userId,
        Long kakaoUserId,
        String uuid,
        String nickname,
        Boolean favorite,
        boolean registeredRecipient
) {
}
