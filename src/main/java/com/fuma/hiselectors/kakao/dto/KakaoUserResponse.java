package com.fuma.hiselectors.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount account) {

    public String nicknameOrDefault() {
        if (account != null && account.profile() != null && account.profile().nickname() != null
                && !account.profile().nickname().isBlank()) {
            return account.profile().nickname();
        }
        return "Kakao-" + id;
    }

    public record KakaoAccount(Profile profile) {
    }

    public record Profile(String nickname) {
    }
}
