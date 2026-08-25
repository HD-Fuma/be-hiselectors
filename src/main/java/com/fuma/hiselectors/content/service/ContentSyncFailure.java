package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import java.util.Objects;

/** 외부에 기록할 수 있도록 민감한 예외 세부 정보를 제거한 콘텐츠 동기화 실패입니다. */
public record ContentSyncFailure(
        String stage,
        SnsPlatform platform,
        String itemType,
        String itemId,
        String errorType,
        String errorMessage) {

    private static final int MAX_IDENTIFIER_LENGTH = 80;

    public ContentSyncFailure {
        stage = normalizeRequired(stage, 40);
        itemType = normalizeRequired(itemType, 40);
        itemId = normalizeRequired(itemId, MAX_IDENTIFIER_LENGTH);
        errorType = normalizeRequired(errorType, 100);
        errorMessage = normalizeRequired(errorMessage, 180);
    }

    static ContentSyncFailure fromException(
            String stage,
            SnsPlatform platform,
            String itemType,
            String itemId,
            RuntimeException exception,
            String stageMessage) {
        Objects.requireNonNull(exception, "예외는 필수입니다.");
        if (exception instanceof BusinessException businessException) {
            return new ContentSyncFailure(
                    stage,
                    platform,
                    itemType,
                    itemId,
                    businessException.getErrorCode().name(),
                    businessException.getErrorCode().getMessage());
        }
        String simpleName = exception.getClass().getSimpleName();
        String safeType = simpleName.isBlank() ? RuntimeException.class.getSimpleName() : simpleName;
        return new ContentSyncFailure(
                stage, platform, itemType, itemId, safeType, stageMessage);
    }

    static ContentSyncFailure stageFailure(
            String stage, RuntimeException exception, String stageMessage) {
        return fromException(stage, null, "stage", "batch", exception, stageMessage);
    }

    String summaryLine() {
        String platformName = platform == null ? "UNKNOWN" : platform.name();
        return stage + " | platform=" + platformName
                + " | " + itemType + "=" + itemId
                + " | " + errorType
                + " | " + errorMessage;
    }

    private static String normalizeRequired(String value, int maxLength) {
        String source = Objects.requireNonNull(value, "실패 요약 값은 필수입니다.");
        StringBuilder sanitized = new StringBuilder(source.length());
        source.codePoints().forEach(codePoint -> sanitized.appendCodePoint(
                isSeparatorOrControl(codePoint) ? ' ' : codePoint));
        String normalized = sanitized.toString()
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("실패 요약 값은 비어 있을 수 없습니다.");
        }
        return truncateSafely(normalized, maxLength);
    }

    static String truncateSafely(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        int endIndex = maxLength;
        if (Character.isHighSurrogate(value.charAt(endIndex - 1))) {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }

    private static boolean isSeparatorOrControl(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL
                || type == Character.SPACE_SEPARATOR
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }
}
