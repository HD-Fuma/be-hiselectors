package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class GeminiRequestExecutor {

    private final GeminiProperties properties;

    public GeminiRequestExecutor(GeminiProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String primaryModel, Function<GeminiProperties.Attempt, T> request) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        RestClientException last = null;
        int attemptNumber = 0;
        for (GeminiProperties.Attempt attempt : properties.attempts(primaryModel)) {
            attemptNumber++;
            try {
                return request.apply(attempt);
            } catch (RestClientException exception) {
                last = exception;
                log.warn("Gemini 호출 실패, 다음 후보로 전환합니다. model={}, attempt={}",
                        attempt.model(), attemptNumber);
            }
        }
        throw last == null ? new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED) : last;
    }
}
