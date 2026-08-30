package com.fuma.hiselectors.content.client;

import com.fuma.hiselectors.exception.ErrorCode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Instagram Graph API 오류를 토큰/계정/한도로 나눈다. */
public final class InstagramGraphApiError {

    private static final Pattern ERROR_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_SUBCODE =
            Pattern.compile("\\\"error_subcode\\\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_MESSAGE =
            Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)");

    private static final Set<Integer> TOKEN_OR_PERMISSION_CODES = Set.of(
            10, 102, 104, 190, 200);
    private static final Set<Integer> RATE_LIMIT_CODES = Set.of(
            4, 17, 32, 613);

    private InstagramGraphApiError() {
    }

    public enum Kind {
        TOKEN_OR_PERMISSION,
        ACCOUNT_UNAVAILABLE,
        RATE_LIMIT,
        OTHER
    }

    public record Classified(
            Kind kind,
            ErrorCode errorCode,
            int httpStatus,
            String graphCode,
            String graphSubcode,
            String message
    ) {
    }

    public static Classified classify(int httpStatus, String responseBody) {
        String code = extract(ERROR_CODE, responseBody);
        String subcode = extract(ERROR_SUBCODE, responseBody);
        String message = safeMessage(responseBody);
        Kind kind = kindOf(httpStatus, parseInt(code), parseInt(subcode), message);
        return new Classified(
                kind, errorCodeOf(kind), httpStatus, code, subcode, message);
    }

    private static Kind kindOf(
            int httpStatus, Integer code, Integer subcode, String message) {
        if (httpStatus == 429 || isRateLimit(code)) {
            return Kind.RATE_LIMIT;
        }
        if (httpStatus == 401 || httpStatus == 403 || isTokenOrPermission(code, subcode)) {
            return Kind.TOKEN_OR_PERMISSION;
        }
        if (isUnavailableAccount(code, subcode, message)) {
            return Kind.ACCOUNT_UNAVAILABLE;
        }
        return Kind.OTHER;
    }

    private static boolean isRateLimit(Integer code) {
        return code != null && (RATE_LIMIT_CODES.contains(code) || code >= 80_000);
    }

    private static boolean isTokenOrPermission(Integer code, Integer subcode) {
        // code=10은 권한 오류. 계정 없음(110+2207013)과 섞지 않는다.
        return code != null && TOKEN_OR_PERMISSION_CODES.contains(code)
                && !isUnavailableAccount(code, subcode, "");
    }

    private static boolean isUnavailableAccount(
            Integer code, Integer subcode, String message) {
        if (code != null && code == 110 && subcode != null && subcode == 2_207_013) {
            return true;
        }
        String normalized = message == null ? "" : message.toLowerCase();
        return normalized.contains("invalid user")
                || normalized.contains("cannot find user")
                || normalized.contains("user not found")
                || normalized.contains("unsupported get request");
    }

    private static ErrorCode errorCodeOf(Kind kind) {
        return switch (kind) {
            case TOKEN_OR_PERMISSION -> ErrorCode.INSTAGRAM_TOKEN_OR_PERMISSION_DENIED;
            case ACCOUNT_UNAVAILABLE -> ErrorCode.INSTAGRAM_ACCOUNT_UNAVAILABLE;
            case RATE_LIMIT -> ErrorCode.INSTAGRAM_API_RATE_LIMITED;
            case OTHER -> ErrorCode.INSTAGRAM_API_CALL_FAILED;
        };
    }

    private static String safeMessage(String responseBody) {
        String message = extract(ERROR_MESSAGE, responseBody);
        if (message.isBlank()) {
            return "응답 메시지 없음";
        }
        String singleLine = message.replaceAll("[\\r\\n\\t]", " ");
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300);
    }

    private static String extract(Pattern pattern, String value) {
        if (value == null) {
            return "";
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
