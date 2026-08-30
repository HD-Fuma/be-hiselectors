package com.fuma.hiselectors.content.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class InstagramGraphApiErrorTest {

    @Test
    void classifiesExpiredToken() {
        InstagramGraphApiError.Classified error = InstagramGraphApiError.classify(
                401, """
                        {"error":{"message":"Invalid OAuth access token.","code":190}}
                        """);

        assertThat(error.kind()).isEqualTo(InstagramGraphApiError.Kind.TOKEN_OR_PERMISSION);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.INSTAGRAM_TOKEN_OR_PERMISSION_DENIED);
        assertThat(error.graphCode()).isEqualTo("190");
    }

    @Test
    void classifiesPermissionDeniedAsTokenOrPermission() {
        InstagramGraphApiError.Classified error = InstagramGraphApiError.classify(
                403, """
                        {"error":{"message":"Application does not have permission","code":10}}
                        """);

        assertThat(error.kind()).isEqualTo(InstagramGraphApiError.Kind.TOKEN_OR_PERMISSION);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.INSTAGRAM_TOKEN_OR_PERMISSION_DENIED);
    }

    @Test
    void classifiesBusinessDiscoveryUnavailableAccount() {
        InstagramGraphApiError.Classified error = InstagramGraphApiError.classify(
                400, """
                        {"error":{"message":"Invalid user id","code":110,"error_subcode":2207013}}
                        """);

        assertThat(error.kind()).isEqualTo(InstagramGraphApiError.Kind.ACCOUNT_UNAVAILABLE);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.INSTAGRAM_ACCOUNT_UNAVAILABLE);
        assertThat(error.graphSubcode()).isEqualTo("2207013");
    }

    @Test
    void classifiesRateLimitByHttpStatusAndCode() {
        assertThat(InstagramGraphApiError.classify(429, "{}").kind())
                .isEqualTo(InstagramGraphApiError.Kind.RATE_LIMIT);
        assertThat(InstagramGraphApiError.classify(
                400, """
                        {"error":{"message":"Application request limit reached","code":4}}
                        """).errorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_API_RATE_LIMITED);
        assertThat(InstagramGraphApiError.classify(
                400, """
                        {"error":{"message":"rate limited","code":80001}}
                        """).kind())
                .isEqualTo(InstagramGraphApiError.Kind.RATE_LIMIT);
    }

    @Test
    void keepsUnknownErrorsAsOther() {
        InstagramGraphApiError.Classified error = InstagramGraphApiError.classify(
                400, """
                        {"error":{"message":"permission denied","code":100}}
                        """);

        assertThat(error.kind()).isEqualTo(InstagramGraphApiError.Kind.OTHER);
        assertThat(error.errorCode()).isEqualTo(ErrorCode.INSTAGRAM_API_CALL_FAILED);
    }
}
