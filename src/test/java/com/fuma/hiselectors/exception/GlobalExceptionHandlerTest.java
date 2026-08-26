package com.fuma.hiselectors.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.common.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void ignoresWrappedDisconnectedClientWithoutWritingAnotherResponse() {
        ResponseEntity<ApiResult<Void>> response = handler.handleNotWritable(
                new HttpMessageNotWritableException("write failed",
                        new AsyncRequestNotUsableException("response is no longer usable")));

        assertThat(response).isNull();
    }

    @Test
    void handlesMessageNotWritableWithoutDisconnectAsInternalServerError() {
        ResponseEntity<ApiResult<Void>> response = handler.handleNotWritable(
                new HttpMessageNotWritableException("serialization failed"));

        assertInternalServerError(response);
    }

    @Test
    void doesNotTreatDisconnectPhraseInUnrelatedExceptionAsClientDisconnect() {
        ResponseEntity<ApiResult<Void>> response = handler.handleUnexpected(
                new IllegalStateException("connection reset by peer"));

        assertInternalServerError(response);
    }

    private static void assertInternalServerError(ResponseEntity<ApiResult<Void>> response) {
        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getStatus());
        assertThat(response.getBody()).isEqualTo(ApiResult.error(
                ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
