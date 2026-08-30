package com.fuma.hiselectors.inspection.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.config.ContentInspectionAnalysisProperties;
import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import com.fuma.hiselectors.stt.GeminiProperties;
import org.junit.jupiter.api.Test;

class ContentGeminiRequestExecutorTest {

    @Test
    void usesSharedGeminiKeysWhenExtractionKeysAreMissing() {
        InspectionExtractionProperties extraction = new InspectionExtractionProperties(
                new InspectionExtractionProperties.Instagram("stt", "ocr", null),
                new InspectionExtractionProperties.Youtube(
                        "", "", "gemini-test", null, 2048, "v1beta"));
        GeminiProperties gemini = new GeminiProperties(
                "shared-key", null, null, "gemini-3.5-flash-lite", null, null);
        ContentGeminiRequestExecutor executor = new ContentGeminiRequestExecutor(
                extraction, null, gemini);

        ContentGeminiRequestExecutor.Execution<String> execution =
                executor.execute(attempt -> {
                    assertThat(attempt.apiKey()).isEqualTo("shared-key");
                    assertThat(attempt.model()).isEqualTo("gemini-test");
                    return "ok";
                });

        assertThat(execution.value()).isEqualTo("ok");
    }

    @Test
    void failsWhenNoGeminiKeyIsConfigured() {
        InspectionExtractionProperties extraction = new InspectionExtractionProperties(
                new InspectionExtractionProperties.Instagram("stt", "ocr", null),
                new InspectionExtractionProperties.Youtube(
                        null, null, "gemini-test", null, 2048, "v1beta"));
        ContentGeminiRequestExecutor executor = new ContentGeminiRequestExecutor(
                extraction,
                new ContentInspectionAnalysisProperties(null, null, null, null, null),
                new GeminiProperties(null, null, null, null, null, null));

        assertThatThrownBy(() -> executor.execute(attempt -> "ok"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GEMINI_API_KEY_MISSING);
    }
}
