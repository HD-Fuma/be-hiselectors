package com.fuma.hiselectors.inspection.extraction;

import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;

public record ContentExtractionExecutionResult(
        ContentMediaExtractionResult extraction,
        String providerRequestId,
        String requestedModel,
        String selectedModel,
        String responseModel,
        long latencyMs,
        int attemptCount,
        Integer inputTokens,
        Integer outputTokens,
        Integer thoughtTokens,
        Integer totalTokens
) {
}
