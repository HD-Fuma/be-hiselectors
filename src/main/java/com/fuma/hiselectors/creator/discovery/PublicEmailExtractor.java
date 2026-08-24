package com.fuma.hiselectors.creator.discovery;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 공개 프로필 텍스트에서 creator_pool 컬럼에 안전하게 저장할 첫 이메일을 찾는다. */
@Component
public class PublicEmailExtractor {

    private static final int MAX_EMAIL_LENGTH = 100;
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})"
                    + "(?![A-Z0-9_%+-])");
    private static final Pattern DOMAIN_LABEL = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    public Optional<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = EMAIL.matcher(text);
        while (matcher.find()) {
            String email = matcher.group(1);
            if (isValid(email)) {
                return Optional.of(email);
            }
        }
        return Optional.empty();
    }

    private boolean isValid(String email) {
        if (email.length() > MAX_EMAIL_LENGTH) {
            return false;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (local.length() > 64 || local.startsWith(".") || local.endsWith(".")
                || local.contains("..") || domain.contains("..")) {
            return false;
        }
        for (String label : domain.split("\\.")) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }
}
