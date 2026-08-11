package com.fuma.hiselectors.youtube.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

///로그인한 구글 계정의 이메일을 확인
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserInfoResponse(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name
) {
}
