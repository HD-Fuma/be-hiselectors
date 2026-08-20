package com.fuma.hiselectors.oauth;

import com.fuma.hiselectors.application.model.SnsPlatform;
import io.jsonwebtoken.Claims;
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

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String STATE_PURPOSE = "oauth-state";
    private static final String VERIFICATION_PURPOSE = "application-oauth-verification";
    private static final String SNS_CODE_CLAIM = "snsCode";
    private static final String SNS_ACCOUNT_ID_CLAIM = "snsAccountId";
    private static final String FOLLOWER_COUNT_CLAIM = "followerCount";
    private static final String CONTENT_COUNT_CLAIM = "contentCount";

    private final SecretKey key;
    private final long stateValidityMillis;
    private final long verificationValidityMillis;

    public OAuthStateProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${oauth.state-validity-seconds:300}") long stateValiditySeconds,
            @Value("${oauth.verification-validity-seconds:900}") long verificationValiditySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.stateValidityMillis = stateValiditySeconds * 1000;
        this.verificationValidityMillis = verificationValiditySeconds * 1000;
    }

    public String create(String hiId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(hiId)
                .claim(PURPOSE_CLAIM, STATE_PURPOSE)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + stateValidityMillis))
                .signWith(key)
                .compact();
    }

    public String resolveHiId(String state) {
        try {
            Claims claims = parseClaims(state, STATE_PURPOSE);
            return requiredText(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 state", e);
        }
    }

    public String createVerificationToken(
            String hiId,
            SnsPlatform snsCode,
            String snsAccountId,
            Long followerCount,
            Long contentCount) {
        if (hiId == null || hiId.isBlank() || snsCode == null
                || snsAccountId == null || snsAccountId.isBlank()
                || isNegative(followerCount) || isNegative(contentCount)) {
            throw new IllegalArgumentException("유효하지 않은 SNS 인증 정보");
        }

        Date now = new Date();
        var builder = Jwts.builder()
                .subject(hiId)
                .claim(PURPOSE_CLAIM, VERIFICATION_PURPOSE)
                .claim(SNS_CODE_CLAIM, snsCode.name())
                .claim(SNS_ACCOUNT_ID_CLAIM, snsAccountId)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + verificationValidityMillis));
        if (followerCount != null) {
            builder.claim(FOLLOWER_COUNT_CLAIM, followerCount);
        }
        if (contentCount != null) {
            builder.claim(CONTENT_COUNT_CLAIM, contentCount);
        }
        return builder.signWith(key).compact();
    }

    public VerifiedAccount resolveVerificationToken(String token, String expectedHiId) {
        try {
            Claims claims = parseClaims(token, VERIFICATION_PURPOSE);
            String hiId = requiredText(claims.getSubject());
            if (!hiId.equals(expectedHiId)) {
                throw new IllegalArgumentException("인증 사용자 불일치");
            }
            SnsPlatform snsCode = SnsPlatform.valueOf(
                    requiredText(claims.get(SNS_CODE_CLAIM, String.class)));
            String snsAccountId = requiredText(
                    claims.get(SNS_ACCOUNT_ID_CLAIM, String.class));
            return new VerifiedAccount(
                    hiId,
                    snsCode,
                    snsAccountId,
                    nullableNonNegativeLong(claims, FOLLOWER_COUNT_CLAIM),
                    nullableNonNegativeLong(claims, CONTENT_COUNT_CLAIM));
        } catch (JwtException | IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 SNS 인증 토큰", e);
        }
    }

    private Claims parseClaims(String token, String expectedPurpose) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedPurpose.equals(claims.get(PURPOSE_CLAIM, String.class))) {
            throw new IllegalArgumentException("토큰 용도 불일치");
        }
        return claims;
    }

    private String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("필수 claim 누락");
        }
        return value;
    }

    private Long nullableNonNegativeLong(Claims claims, String claimName) {
        Object value = claims.get(claimName);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number) || number.longValue() < 0) {
            throw new IllegalArgumentException("수치 claim 오류");
        }
        return number.longValue();
    }

    private boolean isNegative(Long value) {
        return value != null && value < 0;
    }

    public record VerifiedAccount(
            String hiId,
            SnsPlatform snsCode,
            String snsAccountId,
            Long followerCount,
            Long contentCount) {
    }
}
