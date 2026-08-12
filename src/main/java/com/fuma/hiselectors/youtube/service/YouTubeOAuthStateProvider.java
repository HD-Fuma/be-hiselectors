package com.fuma.hiselectors.youtube.service;

import com.fuma.hiselectors.youtube.config.YouTubeOAuthProperties;
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
public class YouTubeOAuthStateProvider {

    private final SecretKey key;
    private final long validityMillis;

    public YouTubeOAuthStateProvider(
            @Value("${jwt.secret}") String secret,
            YouTubeOAuthProperties properties) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = properties.stateValiditySeconds() * 1000;
    }

    public String create(String hiId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(hiId)
                .id(UUID.randomUUID().toString()) // nonce (일회성 식별자)
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
