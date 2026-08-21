package com.fuma.hiselectors.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.application.model.SnsPlatform;
import org.junit.jupiter.api.Test;

class OAuthStateProviderTest {

    private static final String SECRET =
            "test-secret-key-that-is-long-enough-for-hmac-sha-signatures-1234567890";
    private final OAuthStateProvider provider = new OAuthStateProvider(SECRET, 300, 900);

    @Test
    void verificationTokenCarriesCanonicalVerifiedAccountValues() {
        String token = provider.createVerificationToken(
                "hi-user", SnsPlatform.INSTAGRAM, "creator.handle", 12_345L, 120L);

        var verified = provider.resolveVerificationToken(token, "hi-user");

        assertThat(verified.hiId()).isEqualTo("hi-user");
        assertThat(verified.snsCode()).isEqualTo(SnsPlatform.INSTAGRAM);
        assertThat(verified.snsAccountId()).isEqualTo("creator.handle");
        assertThat(verified.followerCount()).isEqualTo(12_345L);
        assertThat(verified.contentCount()).isEqualTo(120L);
    }

    @Test
    void verificationTokenPreservesUnknownCountsAsNull() {
        String token = provider.createVerificationToken(
                "hi-user", SnsPlatform.YOUTUBE, "UC123", null, null);

        var verified = provider.resolveVerificationToken(token, "hi-user");

        assertThat(verified.followerCount()).isNull();
        assertThat(verified.contentCount()).isNull();
    }

    @Test
    void stateAndVerificationTokensCannotBeUsedForEachOther() {
        String state = provider.create("hi-user");
        String verification = provider.createVerificationToken(
                "hi-user", SnsPlatform.YOUTUBE, "UC123", 100L, 10L);

        assertThatThrownBy(() -> provider.resolveVerificationToken(state, "hi-user"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.resolveHiId(verification))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verificationTokenCannotBeUsedByDifferentLogin() {
        String token = provider.createVerificationToken(
                "hi-user", SnsPlatform.YOUTUBE, "UC123", 100L, 10L);

        assertThatThrownBy(() -> provider.resolveVerificationToken(token, "other-user"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredVerificationTokenIsRejected() {
        OAuthStateProvider expiredProvider = new OAuthStateProvider(SECRET, 300, -1);
        String token = expiredProvider.createVerificationToken(
                "hi-user", SnsPlatform.YOUTUBE, "UC123", 100L, 10L);

        assertThatThrownBy(() -> expiredProvider.resolveVerificationToken(token, "hi-user"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
