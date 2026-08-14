package com.fuma.hiselectors.kakao.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoApiExceptionTest {

    @Test
    @DisplayName("친구 관계 오류는 UUID 무효로 판단하지 않는다")
    void friendRelationErrorIsNotInvalidReceiver() {
        KakaoApiException exception = new KakaoApiException(
                400, -502, "receiver is not a friend");

        assertThat(exception.isInvalidReceiver()).isFalse();
    }

    @Test
    @DisplayName("receiver UUID 파라미터 오류를 수신자 무효로 판단한다")
    void invalidReceiverUuid() {
        KakaoApiException exception = new KakaoApiException(
                400, -2, "Constraints on the parameter [receiver_uuids] are unsatisfied.");

        assertThat(exception.isInvalidReceiver()).isTrue();
    }

    @Test
    @DisplayName("같은 오류 코드의 템플릿 오류는 수신자 무효로 판단하지 않는다")
    void templateErrorIsNotInvalidReceiver() {
        KakaoApiException exception = new KakaoApiException(
                400, -2, "template_object is invalid");

        assertThat(exception.isInvalidReceiver()).isFalse();
    }
}
