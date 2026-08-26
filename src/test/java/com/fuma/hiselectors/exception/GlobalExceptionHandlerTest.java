package com.fuma.hiselectors.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.common.ApiResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void ignoresDisconnectedClientWithoutWritingAnotherResponse() {
        ResponseEntity<ApiResult<Void>> response = handler.handleUnexpected(
                new AsyncRequestNotUsableException("response is no longer usable"));

        assertThat(response).isNull();
    }

    @Test
    void handlesOtherUnexpectedExceptionsAsInternalServerErrors() {
        ResponseEntity<ApiResult<Void>> response = handler.handleUnexpected(
                new IllegalStateException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getStatus());
        assertThat(response.getBody()).isEqualTo(ApiResult.error(
                ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
