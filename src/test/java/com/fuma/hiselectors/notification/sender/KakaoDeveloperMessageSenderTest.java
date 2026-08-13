package com.fuma.hiselectors.notification.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.client.KakaoApiException;
import com.fuma.hiselectors.kakao.client.KakaoMessageClient;
import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;
import com.fuma.hiselectors.kakao.service.KakaoTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoDeveloperMessageSenderTest {

    @Test
    @DisplayName("카카오 수신자 UUID 오류를 전용 비즈니스 오류로 변환한다")
    void translateInvalidReceiver() {
        KakaoTokenService tokenService = mock(KakaoTokenService.class);
        KakaoMessageClient messageClient = mock(KakaoMessageClient.class);
        KakaoMessageTemplate template = mock(KakaoMessageTemplate.class);
        KakaoDeveloperMessageSender sender = new KakaoDeveloperMessageSender(
                tokenService, messageClient);
        when(tokenService.getValidAccessToken(1L)).thenReturn("access-token");
        doThrow(new KakaoApiException(
                400, -2, "Constraints on the parameter [receiver_uuids] are unsatisfied."))
                .when(messageClient).sendFriend("access-token", "uuid", template);

        assertThatThrownBy(() -> sender.sendToFriend(1L, "uuid", template))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.KAKAO_RECIPIENT_INVALID));
    }
}
