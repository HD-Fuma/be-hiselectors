package com.fuma.hiselectors.content.service;

import java.util.ArrayList;
import java.util.List;

final class ContentSyncFailureFormatter {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private ContentSyncFailureFormatter() {
    }

    static String format(List<ContentSyncFailure> failures, int additionalFailureCount) {
        List<String> sourceLines = failures.stream()
                .limit(3)
                .map(ContentSyncFailure::summaryLine)
                .toList();
        List<String> lines = new ArrayList<>(sourceLines.size() + 1);
        String additionalLine = additionalFailureCount > 0
                ? "+" + additionalFailureCount + "건의 추가 실패"
                : null;
        int suffixLength = additionalLine == null ? 0 : additionalLine.length() + 1;
        int representativeSeparators = Math.max(0, sourceLines.size() - 1);
        int remainingBudget = MAX_MESSAGE_LENGTH - suffixLength - representativeSeparators;

        for (int index = 0; index < sourceLines.size(); index++) {
            String line = sourceLines.get(index);
            int remainingLines = sourceLines.size() - index;
            int lineLimit = Math.max(1, remainingBudget / remainingLines);
            String fitted = ContentSyncFailure.truncateSafely(line, lineLimit);
            lines.add(fitted);
            remainingBudget -= fitted.length();
        }
        if (additionalLine != null) {
            lines.add(additionalLine);
        }
        return String.join("\n", lines);
    }
}
