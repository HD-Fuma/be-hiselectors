package com.fuma.hiselectors.kakao.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.client.KakaoApiException;
import com.fuma.hiselectors.kakao.client.KakaoOAuthClient;
import com.fuma.hiselectors.kakao.config.KakaoOAuthProperties;
import com.fuma.hiselectors.kakao.dto.KakaoFriendResponse;
import com.fuma.hiselectors.kakao.dto.KakaoRecipientConnectionResponse;
import com.fuma.hiselectors.kakao.dto.KakaoSenderConnectionResponse;
import com.fuma.hiselectors.kakao.dto.KakaoTokenResponse;
import com.fuma.hiselectors.kakao.dto.KakaoUserResponse;
import com.fuma.hiselectors.kakao.model.KakaoSenderConnection;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.kakao.oauth.KakaoConnectionType;
import com.fuma.hiselectors.kakao.oauth.KakaoOAuthState;
import com.fuma.hiselectors.kakao.oauth.KakaoOAuthStateProvider;
import com.fuma.hiselectors.kakao.repository.KakaoSenderConnectionRepository;
import com.fuma.hiselectors.kakao.repository.UserKakaoRecipientRepository;
import com.fuma.hiselectors.kakao.security.KakaoTokenCrypto;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KakaoOAuthService {

    private static final String AUTHORIZE_URI = "https://kauth.kakao.com/oauth/authorize";

    private final KakaoOAuthProperties properties;
    private final KakaoOAuthStateProvider stateProvider;
    private final KakaoOAuthClient oauthClient;
    private final KakaoTokenCrypto tokenCrypto;
    private final KakaoFriendService friendService;
    private final KakaoSenderConnectionRepository senderRepository;
    private final UserKakaoRecipientRepository recipientRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;

    public String buildAuthorizationUrl(String loginId, KakaoConnectionType type) {
        validateConfiguration();
        String scope = type == KakaoConnectionType.SENDER
                ? properties.senderScope() : properties.recipientScope();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URI)
                .queryParam("client_id", properties.restApiKey())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("state", stateProvider.create(loginId, type))
                .queryParam("prompt", "select_account")
                .build().encode().toUriString();
    }

    @Transactional
    public KakaoSenderConnectionResponse connectSender(String code, String state,
                                                        String requesterLoginId) {
        validateState(state, requesterLoginId, KakaoConnectionType.SENDER);
        Admin admin = adminRepository.findByLoginId(requesterLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        KakaoTokenResponse token = exchange(code, properties.senderScope());
        KakaoUserResponse kakaoUser = getUser(token.accessToken());
        LocalDateTime now = LocalDateTime.now();

        KakaoSenderConnection connection = senderRepository.findByKakaoUserId(kakaoUser.id())
                .orElseGet(() -> KakaoSenderConnection.builder()
                        .kakaoUserId(kakaoUser.id())
                        .senderName(kakaoUser.nicknameOrDefault())
                        .accessTokenEncrypted(tokenCrypto.encrypt(token.accessToken()))
                        .refreshTokenEncrypted(tokenCrypto.encrypt(token.refreshToken()))
                        .accessTokenExpiresAt(now.plusSeconds(token.expiresIn()))
                        .refreshTokenExpiresAt(now.plusSeconds(token.refreshTokenExpiresIn()))
                        .build());
        if (connection.getId() != null) {
            connection.updateOAuthConnection(kakaoUser.id(), kakaoUser.nicknameOrDefault(),
                    tokenCrypto.encrypt(token.accessToken()), tokenCrypto.encrypt(token.refreshToken()),
                    now.plusSeconds(token.expiresIn()), now.plusSeconds(token.refreshTokenExpiresIn()));
        }
        connection = senderRepository.save(connection);
        admin.selectKakaoSenderConnection(connection.getId());
        return KakaoSenderConnectionResponse.from(connection);
    }

    @Transactional
    public KakaoRecipientConnectionResponse connectRecipient(String code, String state,
                                                               String requesterHiId) {
        validateState(state, requesterHiId, KakaoConnectionType.RECIPIENT);
        User user = userRepository.findByHiId(requesterHiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));
        KakaoTokenResponse token = exchange(code, properties.recipientScope());
        KakaoUserResponse kakaoUser = getUser(token.accessToken());

        recipientRepository.findByKakaoUserId(kakaoUser.id())
                .filter(existing -> !existing.getUserId().equals(user.getId()))
                .ifPresent(existing -> { throw new BusinessException(ErrorCode.KAKAO_CONNECTION_DUPLICATED); });

        KakaoFriendResponse.Friend matchedFriend = findFromAnySender(kakaoUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.KAKAO_FRIEND_NOT_FOUND));
        recipientRepository.findByKakaoMessageUuid(matchedFriend.uuid())
                .filter(existing -> !existing.getUserId().equals(user.getId()))
                .ifPresent(existing -> { throw new BusinessException(ErrorCode.KAKAO_CONNECTION_DUPLICATED); });

        UserKakaoRecipient recipient = recipientRepository.findByUserId(user.getId())
                .orElseGet(() -> UserKakaoRecipient.builder()
                        .userId(user.getId())
                        .kakaoUserId(kakaoUser.id())
                        .kakaoMessageUuid(matchedFriend.uuid())
                        .build());
        recipient.updateConnection(kakaoUser.id(), matchedFriend.uuid());
        try {
            return KakaoRecipientConnectionResponse.from(recipientRepository.saveAndFlush(recipient));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.KAKAO_CONNECTION_DUPLICATED);
        }
    }

    private Optional<KakaoFriendResponse.Friend> findFromAnySender(Long kakaoUserId) {
        if (senderRepository.count() == 0) {
            throw new BusinessException(ErrorCode.KAKAO_SENDER_NOT_CONNECTED);
        }
        for (KakaoSenderConnection sender : senderRepository.findAll()) {
            try {
                Optional<KakaoFriendResponse.Friend> friend = friendService.findFriend(sender.getId(), kakaoUserId);
                if (friend.isPresent()) {
                    return friend;
                }
            } catch (BusinessException ignored) {
                // 다른 연결된 발신 계정으로 계속 탐색한다.
            }
        }
        return Optional.empty();
    }

    private KakaoTokenResponse exchange(String code, String requiredScope) {
        try {
            KakaoTokenResponse token = oauthClient.exchangeCode(code);
            validateScope(token.scope(), requiredScope);
            if (token.refreshToken() == null || token.refreshTokenExpiresIn() == null) {
                throw new BusinessException(ErrorCode.KAKAO_OAUTH_FAILED);
            }
            return token;
        } catch (KakaoApiException e) {
            throw new BusinessException(ErrorCode.KAKAO_OAUTH_FAILED);
        }
    }

    private KakaoUserResponse getUser(String accessToken) {
        try {
            return oauthClient.getUser(accessToken);
        } catch (KakaoApiException e) {
            throw new BusinessException(ErrorCode.KAKAO_OAUTH_FAILED);
        }
    }

    private void validateScope(String actualScope, String requiredScope) {
        Set<String> actual = actualScope == null ? Set.of()
                : new HashSet<>(Arrays.asList(actualScope.split("\\s+")));
        for (String required : requiredScope.split(",")) {
            if (!actual.contains(required.trim())) {
                throw new BusinessException(ErrorCode.KAKAO_REQUIRED_SCOPE_MISSING);
            }
        }
    }

    private void validateState(String state, String loginId, KakaoConnectionType type) {
        try {
            KakaoOAuthState resolved = stateProvider.resolve(state);
            if (!loginId.equals(resolved.loginId()) || type != resolved.connectionType()) {
                throw new BusinessException(ErrorCode.KAKAO_STATE_INVALID);
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.KAKAO_STATE_INVALID);
        }
    }

    private void validateConfiguration() {
        if (properties.restApiKey() == null || properties.restApiKey().isBlank()
                || properties.redirectUri() == null || properties.redirectUri().isBlank()) {
            throw new BusinessException(ErrorCode.KAKAO_CONFIGURATION_INVALID);
        }
    }
}
