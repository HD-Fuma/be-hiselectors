package com.fuma.hiselectors.kakao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.kakao.client.KakaoOAuthClient;
import com.fuma.hiselectors.kakao.model.KakaoSenderConnection;
import com.fuma.hiselectors.kakao.repository.KakaoSenderConnectionRepository;
import com.fuma.hiselectors.kakao.security.KakaoTokenCrypto;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({KakaoTokenService.class, CacheConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KakaoTokenServiceTransactionTest {

    @Autowired
    private KakaoTokenService tokenService;

    @Autowired
    private KakaoSenderConnectionRepository senderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private KakaoOAuthClient oauthClient;

    @MockitoBean
    private KakaoTokenCrypto tokenCrypto;

    @Test
    void obtainsLockedTokenInNewTransactionAfterCommit() {
        KakaoSenderConnection connection = senderRepository.save(senderConnection());
        when(tokenCrypto.decrypt("encrypted-access")).thenReturn("access-token");
        AtomicReference<String> resolvedToken = new AtomicReference<>();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                resolvedToken.set(tokenService.getValidAccessToken(
                                        connection.getId()));
                            }
                        }));

        assertThat(resolvedToken.get()).isEqualTo("access-token");
    }

    private KakaoSenderConnection senderConnection() {
        LocalDateTime now = LocalDateTime.now();
        return KakaoSenderConnection.builder()
                .kakaoUserId(100L)
                .senderName("sender")
                .accessTokenEncrypted("encrypted-access")
                .refreshTokenEncrypted("encrypted-refresh")
                .accessTokenExpiresAt(now.plusHours(1))
                .refreshTokenExpiresAt(now.plusDays(30))
                .build();
    }
}
