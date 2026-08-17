package com.fuma.hiselectors.content.classifier;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SelectorsUrlEvidenceExtractor {

    private static final Pattern HTTP_URL = Pattern.compile(
            "(?i)(?<![a-z0-9])https?://[^\\s]+"
    );
    private static final String TRAILING_PUNCTUATION = "., !;:)\'\"]}>".replace(" ", "");

    private SelectorsUrlEvidenceExtractor() {
    }

    static Result extract(String text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = HTTP_URL.matcher(text);
        StringBuilder masked = new StringBuilder(text);
        Set<String> matched = new TreeSet<>();
        Set<String> trusted = new TreeSet<>();
        List<int[]> spans = new ArrayList<>();
        while (matcher.find()) {
            String candidate = stripTrailingPunctuation(matcher.group());
            if (candidate.isEmpty()) {
                continue;
            }
            matched.add(candidate);
            if (isTrusted(candidate)) {
                trusted.add(candidate);
            }
            spans.add(new int[] {matcher.start(), matcher.start() + candidate.length()});
        }
        for (int[] span : spans) {
            masked.replace(span[0], span[1], " ".repeat(span[1] - span[0]));
        }
        return new Result(masked.toString(), Set.of(), Set.of(),
                List.copyOf(matched), List.copyOf(trusted));
    }

    private static String stripTrailingPunctuation(String candidate) {
        int end = candidate.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(candidate.charAt(end - 1)) >= 0) {
            end--;
        }
        return candidate.substring(0, end);
    }

    private static boolean isTrusted(String candidate) {
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            if (!"hi.thehyundai.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null) {
                return false;
            }
            int port = uri.getPort();
            return port == -1 || (scheme.equalsIgnoreCase("http") && port == 80)
                    || (scheme.equalsIgnoreCase("https") && port == 443);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            return false;
        }
    }

    record Result(
            String textWithoutUrls,
            Set<SelectorsContentEvidence> evidence,
            Set<String> referralCodes,
            List<String> matchedUrls,
            List<String> trustedUrls) {

        Result {
            textWithoutUrls = Objects.requireNonNull(textWithoutUrls, "textWithoutUrls");
            evidence = copyEvidence(evidence);
            referralCodes = copySorted(referralCodes);
            matchedUrls = copySorted(matchedUrls);
            trustedUrls = copySorted(trustedUrls);
        }

        private static Set<SelectorsContentEvidence> copyEvidence(Set<SelectorsContentEvidence> values) {
            Objects.requireNonNull(values, "evidence");
            EnumSet<SelectorsContentEvidence> copy = values.isEmpty()
                    ? EnumSet.noneOf(SelectorsContentEvidence.class)
                    : EnumSet.copyOf(values);
            return Collections.unmodifiableSet(copy);
        }

        private static Set<String> copySorted(Set<String> values) {
            Objects.requireNonNull(values);
            return Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(values)));
        }

        private static List<String> copySorted(List<String> values) {
            Objects.requireNonNull(values);
            return List.copyOf(values.stream()
                    .map(value -> Objects.requireNonNull(value, "collection value"))
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .toList());
        }
    }
}
