package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.config.ContentInspectionAnalysisProperties;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 지원서 Gemini 실행 경로와 독립된 콘텐츠 상세 분석용 재시도 실행기다. */
@Component
public class ContentInspectionGeminiRequestExecutor {

    private final ContentInspectionAnalysisProperties properties;

    public ContentInspectionGeminiRequestExecutor(
            ContentInspectionAnalysisProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(
            String primaryModel,
            Function<ContentInspectionAnalysisProperties.Attempt, T> request) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }
        RestClientException last = null;
        for (ContentInspectionAnalysisProperties.Attempt attempt
                : properties.attempts(primaryModel)) {
            try {
                return request.apply(attempt);
            } catch (RestClientException exception) {
                last = exception;
                if (!retryable(exception)) {
                    throw exception;
                }
            }
        }
        throw last == null ? new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED) : last;
    }

    private boolean retryable(RestClientException exception) {
        if (!(exception instanceof RestClientResponseException response)) {
            return true;
        }
        int status = response.getStatusCode().value();
        return status == 401 || status == 403 || status == 408
                || status == 429 || status >= 500;
    }
}
