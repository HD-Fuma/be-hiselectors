package com.fuma.hiselectors.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String ACCESS_PURPOSE = "access";
    private static final String ROLE_CLAIM = "role";
    private static final Set<String> ACCESS_ROLES = Set.of("USER", "ADMIN");

    private final SecretKey key;
    private final long validityMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-seconds}") long validitySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = validitySeconds * 1000;
    }

    public String createToken(String loginId, String role) {
        if (loginId == null || loginId.isBlank() || !ACCESS_ROLES.contains(role)) {
            throw new IllegalArgumentException("유효하지 않은 access token 정보");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMillis);
        return Jwts.builder()
                .subject(loginId)
                .claim(PURPOSE_CLAIM, ACCESS_PURPOSE)
                .claim(ROLE_CLAIM, role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getLoginId(String token) {
        return parse(token).getSubject();
    }

    public String getRole(String token) {
        return parse(token).get(ROLE_CLAIM, String.class);
    }

    private Claims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String purpose = claims.get(PURPOSE_CLAIM, String.class);
        String role = claims.get(ROLE_CLAIM, String.class);
        String subject = claims.getSubject();
        if ((purpose != null && !ACCESS_PURPOSE.equals(purpose))
                || !ACCESS_ROLES.contains(role)
                || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("access token claim 오류");
        }
        return claims;
    }
}
