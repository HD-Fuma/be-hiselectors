package com.fuma.hiselectors.oauth.instagram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.oauth.OAuthStateProvider;
import com.fuma.hiselectors.oauth.instagram.config.InstagramOAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class InstagramOAuthServiceTest {

    private MockRestServiceServer server;
    private InstagramOAuthService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        OAuthStateProvider stateProvider = mock(OAuthStateProvider.class);
        when(stateProvider.resolveHiId("state")).thenReturn("hi-user");
        service = new InstagramOAuthService(
                new InstagramOAuthProperties("client", "secret", "https://redirect", "scope"),
                stateProvider,
                builder.build());
    }

    @Test
    void authorizationUrlMatchesRegisteredRootCallbackAndForcesFreshLogin() {
        String url = service.buildAuthorizationUrl("hi-user");

        assertThat(url)
                .contains("redirect_uri=https://redirect/")
                .contains("force_reauth=true");
    }

    @Test
    void metaBadRequestMeansExpiredOrReusedAuthorizationCode() {
        server.expect(request -> { })
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error_message\":\"Matching code was not found or was already used\"}"));

        assertThatThrownBy(() -> service.verifyAccountOwnership("used-code", "state", "hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_AUTH_CODE_INVALID);
        server.verify();
    }

    @Test
    void metaServerErrorRemainsBadGateway() {
        server.expect(request -> { })
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.verifyAccountOwnership("code", "state", "hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_OAUTH_FAILED);
        server.verify();
    }

    @Test
    void profileBadRequestIsNotMisreportedAsReusedAuthorizationCode() {
        server.expect(request -> { })
                .andRespond(withSuccess("{\"access_token\":\"token\",\"user_id\":1}", MediaType.APPLICATION_JSON));
        server.expect(request -> { })
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"message\":\"Unsupported field\"}}"));

        assertThatThrownBy(() -> service.verifyAccountOwnership("code", "state", "hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_OAUTH_FAILED);
        server.verify();
    }
}
