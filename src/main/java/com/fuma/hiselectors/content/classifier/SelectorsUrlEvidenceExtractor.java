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
            "https?://[^\\s]+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern PRODUCT_PATH = Pattern.compile(
            "^/product/([A-Za-z0-9_-]{1,100})/?$"
    );
    private static final Pattern SELECTORS_MANAGE_SHOP_PATH = Pattern.compile(
            "^/sellectors/manage/shop/(RC[0-9]{9}T)(?:/1)?/?$", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SELECTORS_GROUP_PATH = Pattern.compile(
            "^/sellectors/([A-Za-z0-9_-]{1,100})/?$"
    );
    private static final Pattern REFERRAL_CODE = Pattern.compile(
            "RC[0-9]{9}T", Pattern.CASE_INSENSITIVE
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
        EnumSet<SelectorsContentEvidence> evidence = EnumSet.noneOf(SelectorsContentEvidence.class);
        Set<String> referralCodes = new TreeSet<>();
        List<int[]> spans = new ArrayList<>();
        while (matcher.find()) {
            String candidate = stripTrailingPunctuation(matcher.group());
            if (candidate.isEmpty()) {
                continue;
            }
            matched.add(candidate);
            if (isTrusted(candidate)) {
                trusted.add(candidate);
                classifyProductUrl(candidate, evidence, referralCodes);
                classifySelectorsShopUrl(candidate, evidence, referralCodes);
            }
            spans.add(new int[] {matcher.start(), matcher.start() + candidate.length()});
        }
        for (int[] span : spans) {
            masked.replace(span[0], span[1], " ".repeat(span[1] - span[0]));
        }
        return new Result(masked.toString(), evidence, referralCodes,
                List.copyOf(matched), List.copyOf(trusted));
    }

    private static void classifyProductUrl(String candidate,
                                           Set<SelectorsContentEvidence> evidence,
                                           Set<String> referralCodes) {
        try {
            URI uri = new URI(candidate);
            String rawPath = uri.getRawPath();
            if (rawPath == null || rawPath.indexOf('%') >= 0 || !PRODUCT_PATH.matcher(rawPath).matches()) {
                return;
            }
            evidence.add(SelectorsContentEvidence.PUBLIC_PRODUCT_URL);

            String rawQuery = uri.getRawQuery();
            if (rawQuery == null) {
                return;
            }
            String referralCode = null;
            int referralNameCount = 0;
            for (String pair : rawQuery.split("&", -1)) {
                int equals = pair.indexOf('=');
                String name = equals < 0 ? pair : pair.substring(0, equals);
                if (!name.equalsIgnoreCase("ptrsRefCd")) {
                    continue;
                }
                referralNameCount++;
                if (referralNameCount > 1 || equals < 0) {
                    continue;
                }
                String value = pair.substring(equals + 1);
                if (REFERRAL_CODE.matcher(value).matches()) {
                    referralCode = value.toUpperCase(java.util.Locale.ROOT);
                }
            }
            if (referralNameCount == 1 && referralCode != null) {
                evidence.add(SelectorsContentEvidence.PRODUCT_URL_WITH_REFERRAL);
                evidence.add(SelectorsContentEvidence.REFERRAL_CODE);
                referralCodes.add(referralCode);
            }
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            // The URL was already trusted, but leave classification conservative if parsing changes.
        }
    }

    private static void classifySelectorsShopUrl(String candidate,
                                                 Set<SelectorsContentEvidence> evidence,
                                                 Set<String> referralCodes) {
        try {
            URI uri = new URI(candidate);
            String rawPath = uri.getRawPath();
            if (rawPath == null || rawPath.indexOf('%') >= 0) {
                return;
            }
            Matcher manageShop = SELECTORS_MANAGE_SHOP_PATH.matcher(rawPath);
            if (manageShop.matches()) {
                evidence.add(SelectorsContentEvidence.SELECTORS_SHOP_URL);
                evidence.add(SelectorsContentEvidence.REFERRAL_CODE);
                referralCodes.add(manageShop.group(1).toUpperCase(java.util.Locale.ROOT));
                return;
            }
            if (SELECTORS_GROUP_PATH.matcher(rawPath).matches()) {
                evidence.add(SelectorsContentEvidence.SELECTORS_SHOP_URL);
            }
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            // The URL was already trusted, but leave classification conservative if parsing changes.
        }
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
                    || uri.getUserInfo() != null
                    || (uri.getRawAuthority() != null && uri.getRawAuthority().endsWith(":"))) {
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
