package com.fuma.hiselectors.content.classifier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SelectorsTextEvidenceExtractor {

    private static final Pattern REFERRAL_CODE = Pattern.compile(
            "RC[0-9]{9}T",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HASHTAG = Pattern.compile(
            "#[\\p{L}\\p{N}_]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SELECTORS_BRAND_PHRASE = Pattern.compile(
            "더현대\\s+셀렉터스", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SELECTORS_NAME = Pattern.compile(
            "셀렉터스|Selectors",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SELECTORS_SHOP_NAME = Pattern.compile(
            "셀렉터스\\s*샵", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern THE_HYUNDAI_PHRASE = Pattern.compile(
            "THE\\s+HYUNDAI",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private SelectorsTextEvidenceExtractor() {
    }

    static Result extract(String normalizedFullText,
                          String textWithoutUrls,
                          Set<SelectorsContentEvidence> urlEvidence) {
        Objects.requireNonNull(normalizedFullText, "normalizedFullText");
        Objects.requireNonNull(textWithoutUrls, "textWithoutUrls");
        Objects.requireNonNull(urlEvidence, "urlEvidence");

        Set<String> referralCodes = new TreeSet<>();
        Matcher referralMatcher = REFERRAL_CODE.matcher(textWithoutUrls);
        while (referralMatcher.find()) {
            if (hasStandaloneBoundaries(textWithoutUrls,
                    referralMatcher.start(), referralMatcher.end())) {
                referralCodes.add(referralMatcher.group().toUpperCase(Locale.ROOT));
            }
        }

        EnumSet<SelectorsContentEvidence> evidence = urlEvidence.isEmpty()
                ? EnumSet.noneOf(SelectorsContentEvidence.class)
                : EnumSet.copyOf(urlEvidence);
        if (!referralCodes.isEmpty()) {
            evidence.add(SelectorsContentEvidence.REFERRAL_CODE);
        }

        Set<String> hashtags = new TreeSet<>();
        Matcher hashtagMatcher = HASHTAG.matcher(textWithoutUrls);
        while (hashtagMatcher.find()) {
            hashtags.add(hashtagMatcher.group());
        }
        boolean hasTheHyundaiHashtag = hashtags.stream()
                .anyMatch(hashtag -> hashtag.substring(1).contains("더현대"));
        if (hasTheHyundaiHashtag && hashtags.contains("#셀렉터스")) {
            evidence.add(SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR);
        }

        String textForTextSignals = maskHashtags(textWithoutUrls);
        boolean hasCombinedSelectorsHashtag = hashtags.contains("#더현대셀렉터스");
        boolean hasSelectorsBrandPhrase = containsStandalone(
                SELECTORS_BRAND_PHRASE, textForTextSignals) || hasCombinedSelectorsHashtag;
        boolean hasSelectorsName = containsStandalone(SELECTORS_NAME, textForTextSignals);
        boolean hasSelectorsShopName = containsStandalone(SELECTORS_SHOP_NAME, textForTextSignals);
        if (hasSelectorsBrandPhrase) {
            evidence.add(SelectorsContentEvidence.SELECTORS_BRAND_PHRASE);
            if (!hasCombinedSelectorsHashtag) {
                hasSelectorsName = true;
            }
        }
        if (hasSelectorsName) {
            evidence.add(SelectorsContentEvidence.SELECTORS_NAME);
        }
        if (hasSelectorsShopName) {
            evidence.add(SelectorsContentEvidence.SELECTORS_SHOP_NAME);
            evidence.add(SelectorsContentEvidence.SELECTORS_NAME);
        }

        boolean hasTheHyundaiMention = hasTheHyundaiHashtag
                || textForTextSignals.contains("더현대")
                || textForTextSignals.contains("현대백화점")
                || containsStandalone(THE_HYUNDAI_PHRASE, textForTextSignals);
        if (hasTheHyundaiMention) {
            evidence.add(SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        }

        int nameFamilyScore = hasSelectorsBrandPhrase ? 5
                : (hasSelectorsName || hasSelectorsShopName ? 4 : 0);
        int score = nameFamilyScore + (hasTheHyundaiMention ? 1 : 0);

        return new Result(evidence, referralCodes, score, hashtags);
    }

    private static boolean containsStandalone(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (hasStandaloneBoundaries(text, matcher.start(), matcher.end())) {
                return true;
            }
        }
        return false;
    }

    private static String maskHashtags(String text) {
        StringBuilder masked = new StringBuilder(text);
        Matcher matcher = HASHTAG.matcher(text);
        while (matcher.find()) {
            for (int index = matcher.start(); index < matcher.end(); index++) {
                masked.setCharAt(index, ' ');
            }
        }
        return masked.toString();
    }

    private static boolean hasStandaloneBoundaries(String text, int start, int end) {
        return (start == 0 || !isUnicodeWordLike(text.codePointBefore(start)))
                && (end == text.length() || !isUnicodeWordLike(text.codePointAt(end)));
    }

    private static boolean isUnicodeWordLike(int codePoint) {
        if (codePoint == '_') {
            return true;
        }
        if (Character.isLetter(codePoint)) {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.DECIMAL_DIGIT_NUMBER,
                    Character.LETTER_NUMBER,
                    Character.OTHER_NUMBER -> true;
            default -> false;
        };
    }

    record Result(
            Set<SelectorsContentEvidence> evidence,
            Set<String> referralCodes,
            int score,
            Set<String> hashtags) {

        Result {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(referralCodes, "referralCodes");
            Objects.requireNonNull(hashtags, "hashtags");
            if (score < 0) {
                throw new IllegalArgumentException("score must not be negative");
            }
            evidence = copyEvidence(evidence);
            referralCodes = copySortedUppercase(referralCodes);
            hashtags = copySorted(hashtags);
        }

        private static Set<SelectorsContentEvidence> copyEvidence(
                Set<SelectorsContentEvidence> values) {
            EnumSet<SelectorsContentEvidence> copy = values.isEmpty()
                    ? EnumSet.noneOf(SelectorsContentEvidence.class)
                    : EnumSet.copyOf(values);
            return Collections.unmodifiableSet(copy);
        }

        private static Set<String> copySortedUppercase(Set<String> values) {
            TreeSet<String> sorted = new TreeSet<>();
            for (String value : values) {
                sorted.add(Objects.requireNonNull(value, "referral code").toUpperCase(Locale.ROOT));
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        }

        private static Set<String> copySorted(Set<String> values) {
            TreeSet<String> sorted = new TreeSet<>();
            for (String value : values) {
                sorted.add(Objects.requireNonNull(value, "hashtag"));
            }
            return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
        }
    }
}
