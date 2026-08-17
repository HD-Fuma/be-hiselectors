package com.fuma.hiselectors.content.classifier;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record SelectorsContentClassification(
        SelectorsContentDecision decision,
        int score,
        SelectorsContentReviewTier reviewTier,
        Set<SelectorsContentEvidence> evidence,
        List<String> referralCodes,
        List<String> matchedUrls,
        String ruleVersion) {

    public SelectorsContentClassification {
        decision = Objects.requireNonNull(decision);
        reviewTier = Objects.requireNonNull(reviewTier);
        evidence = Objects.requireNonNull(evidence);
        referralCodes = Objects.requireNonNull(referralCodes);
        matchedUrls = Objects.requireNonNull(matchedUrls);
        ruleVersion = Objects.requireNonNull(ruleVersion);
        if (ruleVersion.isBlank()) {
            throw new IllegalArgumentException("ruleVersion must not be blank");
        }
        if (score < 0) {
            throw new IllegalArgumentException("score must not be negative");
        }
        EnumSet<SelectorsContentEvidence> evidenceCopy = evidence.isEmpty()
                ? EnumSet.noneOf(SelectorsContentEvidence.class)
                : EnumSet.copyOf(evidence);
        evidence = Collections.unmodifiableSet(evidenceCopy);
        referralCodes = List.copyOf(referralCodes.stream()
                .map(code -> Objects.requireNonNull(code).toUpperCase(Locale.ROOT))
                .distinct().sorted().toList());
        matchedUrls = List.copyOf(matchedUrls.stream()
                .map(Objects::requireNonNull).distinct().sorted().toList());

        Set<SelectorsContentEvidence> decisiveEvidence = EnumSet.of(
                SelectorsContentEvidence.REFERRAL_CODE,
                SelectorsContentEvidence.PRODUCT_URL_WITH_REFERRAL,
                SelectorsContentEvidence.SELECTORS_SHOP_URL,
                SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR);
        switch (decision) {
            case CONFIRMED -> {
                if (decisiveEvidence.stream().noneMatch(evidence::contains)
                        || reviewTier != SelectorsContentReviewTier.NONE) {
                    throw new IllegalArgumentException("confirmed classification requires decisive evidence and no review tier");
                }
            }
            case REVIEW_REQUIRED -> {
                if (score < 3
                        || (score <= 5 && reviewTier != SelectorsContentReviewTier.NORMAL)
                        || (score >= 6 && reviewTier != SelectorsContentReviewTier.STRONG)) {
                    throw new IllegalArgumentException("review tier does not match score");
                }
            }
            case NOT_SELECTORS -> {
                if (score > 2 || reviewTier != SelectorsContentReviewTier.NONE) {
                    throw new IllegalArgumentException("not selectors classification requires score at most two and no review tier");
                }
            }
        }
    }
}
