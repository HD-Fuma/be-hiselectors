package com.fuma.hiselectors.kakao.oauth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KakaoOAuthStateProvider {

    private static final String CONNECTION_TYPE_CLAIM = "kakao_connection_type";

    private final SecretKey key;
    private final long validityMillis;

    public KakaoOAuthStateProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${oauth.state-validity-seconds:300}") long validitySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = validitySeconds * 1000;
    }

    public String create(String loginId, KakaoConnectionType connectionType) {
        Date now = new Date();
        return Jwts.builder()
                .subject(loginId)
                .claim(CONNECTION_TYPE_CLAIM, connectionType.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMillis))
                .signWith(key)
                .compact();
    }

    public KakaoOAuthState resolve(String state) {
        try {
            var claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(state).getPayload();
            return new KakaoOAuthState(
                    claims.getSubject(),
                    KakaoConnectionType.valueOf(claims.get(CONNECTION_TYPE_CLAIM, String.class))
            );
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 카카오 OAuth state입니다.", e);
        }
    }
}
