package com.fuma.hiselectors.kakao.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.client.KakaoApiException;
import com.fuma.hiselectors.kakao.client.KakaoOAuthClient;
import com.fuma.hiselectors.kakao.dto.KakaoTokenResponse;
import com.fuma.hiselectors.kakao.model.KakaoSenderConnection;
import com.fuma.hiselectors.kakao.model.KakaoSenderConnectionStatus;
import com.fuma.hiselectors.kakao.repository.KakaoSenderConnectionRepository;
import com.fuma.hiselectors.kakao.security.KakaoTokenCrypto;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoTokenService {

    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final KakaoSenderConnectionRepository senderRepository;
    private final KakaoOAuthClient oauthClient;
    private final KakaoTokenCrypto tokenCrypto;

    @Transactional(noRollbackFor = BusinessException.class)
    public String getValidAccessToken(Long connectionId) {
        KakaoSenderConnection connection = lockedConnection(connectionId);
        if (connection.getStatus() == KakaoSenderConnectionStatus.REAUTH_REQUIRED) {
            throw new BusinessException(ErrorCode.KAKAO_SENDER_REAUTH_REQUIRED);
        }
        if (connection.getAccessTokenExpiresAt().isAfter(
                LocalDateTime.now().plusSeconds(REFRESH_MARGIN_SECONDS))) {
            return tokenCrypto.decrypt(connection.getAccessTokenEncrypted());
        }
        return refresh(connection);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public String forceRefresh(Long connectionId) {
        return refresh(lockedConnection(connectionId));
    }

    private KakaoSenderConnection lockedConnection(Long connectionId) {
        return senderRepository.findByIdForUpdate(connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_SENDER_NOT_CONNECTED));
    }

    private String refresh(KakaoSenderConnection connection) {
        if (!connection.getRefreshTokenExpiresAt().isAfter(LocalDateTime.now())) {
            connection.markReauthRequired();
            throw new BusinessException(ErrorCode.KAKAO_SENDER_REAUTH_REQUIRED);
        }

        try {
            KakaoTokenResponse response = oauthClient.refreshToken(
                    tokenCrypto.decrypt(connection.getRefreshTokenEncrypted()));
            LocalDateTime now = LocalDateTime.now();
            String encryptedAccessToken = tokenCrypto.encrypt(response.accessToken());
            if (response.refreshToken() != null && response.refreshTokenExpiresIn() != null) {
                connection.updateTokens(
                        encryptedAccessToken,
                        tokenCrypto.encrypt(response.refreshToken()),
                        now.plusSeconds(response.expiresIn()),
                        now.plusSeconds(response.refreshTokenExpiresIn())
                );
            } else {
                connection.updateAccessToken(
                        encryptedAccessToken,
                        now.plusSeconds(response.expiresIn())
                );
            }
            return response.accessToken();
        } catch (KakaoApiException e) {
            if (e.isInvalidToken()) {
                connection.markReauthRequired();
                throw new BusinessException(ErrorCode.KAKAO_SENDER_REAUTH_REQUIRED);
            }
            throw new BusinessException(ErrorCode.KAKAO_TOKEN_REFRESH_FAILED);
        }
    }
}
