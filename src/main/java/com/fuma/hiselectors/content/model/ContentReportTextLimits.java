package com.fuma.hiselectors.content.model;

import java.util.List;

/** Gemini 검수 응답과 저장 컬럼이 공유하는 리포트 문구 길이 한도. */
public final class ContentReportTextLimits {

    public static final int SUMMARY = 400;
    public static final int PURPOSE = 200;
    public static final int FLOW = 500;
    public static final int OVERALL_ASSESSMENT = 500;
    public static final int CONTENT_STYLE = 200;
    public static final int TONE = 120;
    public static final int INSIGHT_ITEM = 120;
    public static final int REASON = 400;

    private ContentReportTextLimits() {
    }

    public static String clip(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxChars);
    }

    public static List<String> clipItems(List<String> values, int maxChars) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> clip(value, maxChars)).toList();
    }
}
