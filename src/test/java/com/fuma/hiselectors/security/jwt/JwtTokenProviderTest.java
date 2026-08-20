package com.fuma.hiselectors.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.oauth.OAuthStateProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtTokenProviderTest {

    private static final String SECRET =
            "test-secret-key-that-is-long-enough-for-hmac-sha-signatures-1234567890";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3_600);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsOnlyAccessTokenPurposeWithSupportedRole() {
        String accessToken = provider.createToken("hi-user", "USER");
        OAuthStateProvider oAuthStateProvider = new OAuthStateProvider(SECRET, 300, 900);
        String verificationToken = oAuthStateProvider.createVerificationToken(
                "hi-user", SnsPlatform.YOUTUBE, "UC123", 100L, 10L);

        assertThat(provider.validate(accessToken)).isTrue();
        assertThat(provider.getLoginId(accessToken)).isEqualTo("hi-user");
        assertThat(provider.getRole(accessToken)).isEqualTo("USER");
        assertThat(provider.validate(verificationToken)).isFalse();
    }

    @Test
    void legacyAccessTokenWithoutPurposeRemainsValidDuringDeployment() {
        Date now = new Date();
        String legacyToken = Jwts.builder()
                .subject("admin")
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.validate(legacyToken)).isTrue();
        assertThat(provider.getRole(legacyToken)).isEqualTo("ADMIN");
    }

    @Test
    void verificationTokenCannotAuthenticateAsBearerToken() throws Exception {
        OAuthStateProvider oAuthStateProvider = new OAuthStateProvider(SECRET, 300, 900);
        String verificationToken = oAuthStateProvider.createVerificationToken(
                "hi-user", SnsPlatform.YOUTUBE, "UC123", 100L, 10L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + verificationToken);

        new JwtAuthenticationFilter(provider).doFilter(
                request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
