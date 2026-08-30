package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GeminiRequestExecutor {

    private final GeminiProperties properties;

    public GeminiRequestExecutor(GeminiProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String primaryModel, Function<GeminiProperties.Attempt, T> request) {
        return executeMeasured(primaryModel, request).value();
    }

    /**
     * 기존 재시도 정책을 실행하면서 실제 요청 횟수와 최종 선택 모델을 함께 반환한다.
     * 인증 실패 후 건너뛴 후보는 실제 요청이 아니므로 attemptCount 에 포함하지 않는다.
     */
    public <T> Execution<T> executeMeasured(
            String primaryModel, Function<GeminiProperties.Attempt, T> request) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        RestClientException last = null;
        String rejectedApiKey = null;
        int attemptNumber = 0;
        for (GeminiProperties.Attempt attempt : properties.attempts(primaryModel)) {
            if (attempt.apiKey().equals(rejectedApiKey)) {
                continue;
            }
            attemptNumber++;
            try {
                return new Execution<>(request.apply(attempt), attemptNumber, attempt.model());
            } catch (RestClientException exception) {
                last = exception;
                if (isAuthenticationFailure(exception)) {
                    rejectedApiKey = attempt.apiKey();
                    log.warn("Gemini 인증 실패, 현재 키의 나머지 모델을 건너뜁니다. model={}, attempt={}",
                            attempt.model(), attemptNumber);
                    continue;
                }
                if (!isRetryable(exception)) {
                    throw exception;
                }
                log.warn("Gemini 호출 실패, 다음 후보로 전환합니다. model={}, attempt={}",
                        attempt.model(), attemptNumber);
            }
        }
        throw last == null ? new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED) : last;
    }

    public record Execution<T>(T value, int attemptCount, String selectedModel) {

        public int retryCount() {
            return Math.max(0, attemptCount - 1);
        }
    }

    private boolean isAuthenticationFailure(RestClientException exception) {
        if (!(exception instanceof RestClientResponseException response)) {
            return false;
        }
        return response.getStatusCode() == HttpStatus.UNAUTHORIZED
                || response.getStatusCode() == HttpStatus.FORBIDDEN;
    }

    private boolean isRetryable(RestClientException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        if (!(exception instanceof RestClientResponseException response)) {
            return false;
        }
        int status = response.getStatusCode().value();
        return status == 404 || status == 408 || status == 429 || status >= 500;
    }
}
