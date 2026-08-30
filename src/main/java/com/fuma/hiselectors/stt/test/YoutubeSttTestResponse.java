package com.fuma.hiselectors.stt.test;

import com.fuma.hiselectors.stt.SttResult;
import com.fuma.hiselectors.stt.YoutubeSttExecutionResult;

public record YoutubeSttTestResponse(
        String videoId,
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

    public static YoutubeSttTestResponse from(
            String videoId, YoutubeSttExecutionResult execution) {
        return new YoutubeSttTestResponse(
                videoId,
                execution.result(),
                execution.requestedModel(),
                execution.selectedModel(),
                execution.responseModel(),
                execution.mediaResolution(),
                execution.latencyMs(),
                execution.attemptCount(),
                execution.retryCount(),
                execution.promptTokens(),
                execution.outputTokens(),
                execution.thoughtTokens(),
                execution.totalTokens());
    }
}
