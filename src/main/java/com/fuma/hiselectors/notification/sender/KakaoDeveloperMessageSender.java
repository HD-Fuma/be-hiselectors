package com.fuma.hiselectors.notification.sender;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.client.KakaoApiException;
import com.fuma.hiselectors.kakao.client.KakaoMessageClient;
import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;
import com.fuma.hiselectors.kakao.service.KakaoTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoDeveloperMessageSender implements NotificationSender {

    private final KakaoTokenService tokenService;
    private final KakaoMessageClient messageClient;

    @Override
    public void sendToMe(Long connectionId, KakaoMessageTemplate template) {
        execute(connectionId, token -> messageClient.sendMe(token, template));
    }

    @Override
    public void sendToFriend(Long connectionId, String receiverUuid,
                             KakaoMessageTemplate template) {
        execute(connectionId, token -> messageClient.sendFriend(token, receiverUuid, template));
    }

    private void execute(Long connectionId, TokenCall call) {
        String token = tokenService.getValidAccessToken(connectionId);
        try {
            call.execute(token);
        } catch (KakaoApiException e) {
            if (e.isInvalidToken()) {
                try {
                    call.execute(tokenService.forceRefresh(connectionId));
                } catch (KakaoApiException retryFailure) {
                    throw translate(retryFailure);
                }
                return;
            }
            throw translate(e);
        }
    }

    private BusinessException translate(KakaoApiException e) {
        if (e.isInsufficientScope()) {
            return new BusinessException(ErrorCode.KAKAO_REQUIRED_SCOPE_MISSING);
        }
        if (e.isInvalidReceiver()) {
            return new BusinessException(ErrorCode.KAKAO_RECIPIENT_INVALID);
        }
        if (Integer.valueOf(-532).equals(e.getKakaoCode())
                || Integer.valueOf(-533).equals(e.getKakaoCode())
                || Integer.valueOf(-536).equals(e.getKakaoCode())) {
            return new BusinessException(ErrorCode.KAKAO_MESSAGE_QUOTA_EXCEEDED);
        }
        return new BusinessException(ErrorCode.KAKAO_MESSAGE_SEND_FAILED);
    }

    @FunctionalInterface
    private interface TokenCall {
        void execute(String token);
    }
}
