package com.fuma.hiselectors.stt;

/** YouTube STT 1회 실행 결과와 Gemini 호출 측정값. */
public record YoutubeSttExecutionResult(
        SttResult result,
        String requestedModel,
        String selectedModel,
        String responseModel,
        String mediaResolution,
        long latencyMs,
        int attemptCount,
        int retryCount,
        Integer promptTokens,
        Integer outputTokens,
        Integer thoughtTokens,
        Integer totalTokens) {
}
