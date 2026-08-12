package com.fuma.hiselectors.oauth;

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
public class OAuthStateProvider {

    private final SecretKey key;
    private final long validityMillis;

    public OAuthStateProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${oauth.state-validity-seconds:300}") long stateValiditySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = stateValiditySeconds * 1000;
    }

    public String create(String hiId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(hiId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMillis))
                .signWith(key)
                .compact();
    }

    public String resolveHiId(String state) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(state)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 state", e);
        }
    }
}
