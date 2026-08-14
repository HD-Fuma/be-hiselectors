package com.fuma.hiselectors.kakao.client;

import java.util.Locale;
import lombok.Getter;

@Getter
public class KakaoApiException extends RuntimeException {

    private final int httpStatus;
    private final Integer kakaoCode;

    public KakaoApiException(int httpStatus, Integer kakaoCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.kakaoCode = kakaoCode;
    }

    public boolean isInvalidToken() {
        return httpStatus == 401 || Integer.valueOf(-401).equals(kakaoCode);
    }

    public boolean isInsufficientScope() {
        return Integer.valueOf(-402).equals(kakaoCode);
    }

    public boolean isInvalidReceiver() {
        if (!Integer.valueOf(-2).equals(kakaoCode) || getMessage() == null) {
            return false;
        }
        String message = getMessage().toLowerCase(Locale.ROOT);
        return message.contains("receiver_uuids")
                || message.contains("receiver id")
                || message.contains("receivers");
    }
}
