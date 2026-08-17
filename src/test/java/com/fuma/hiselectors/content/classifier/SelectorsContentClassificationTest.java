package com.fuma.hiselectors.content.classifier;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectorsContentClassificationTest {

    @Test
    void copiesAndOrdersMutableInputs() {
        Set<SelectorsContentEvidence> evidence = new HashSet<>(Set.of(
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.REFERRAL_CODE,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION));
        List<String> referralCodes = new ArrayList<>(List.of("b2", "A1", "a1"));
        List<String> matchedUrls = new ArrayList<>(List.of("https://z.example", "https://a.example", "https://z.example"));

        SelectorsContentClassification classification = new SelectorsContentClassification(
                SelectorsContentDecision.REVIEW_REQUIRED,
                3,
                SelectorsContentReviewTier.NORMAL,
                evidence,
                referralCodes,
                matchedUrls,
                "v1");

        evidence.clear();
        referralCodes.clear();
        matchedUrls.clear();

        assertEquals(List.of(
                SelectorsContentEvidence.REFERRAL_CODE,
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.copyOf(classification.evidence()));
        assertEquals(List.of("A1", "B2"), classification.referralCodes());
        assertEquals(List.of("https://a.example", "https://z.example"), classification.matchedUrls());
        assertThrows(UnsupportedOperationException.class,
                () -> classification.referralCodes().add("C3"));
        assertThrows(UnsupportedOperationException.class,
                () -> classification.matchedUrls().add("https://b.example"));
    }

    @Test
    void rejectsNullReferences() {
        assertThrows(NullPointerException.class, () -> new SelectorsContentClassification(
                null, 0, SelectorsContentReviewTier.NONE, Set.of(), List.of(), List.of(), "v1"));
        assertThrows(NullPointerException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, 0, null, Set.of(), List.of(), List.of(), "v1"));
        assertThrows(NullPointerException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, 0, SelectorsContentReviewTier.NONE, null, List.of(), List.of(), "v1"));
        assertThrows(NullPointerException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, 0, SelectorsContentReviewTier.NONE, Set.of(), null, List.of(), "v1"));
        assertThrows(NullPointerException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, 0, SelectorsContentReviewTier.NONE, Set.of(), List.of(), null, "v1"));
        assertThrows(NullPointerException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, 0, SelectorsContentReviewTier.NONE, Set.of(), List.of(), List.of(), null));
    }

    @Test
    void rejectsBlankRuleVersion() {
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, 0, SelectorsContentReviewTier.NONE,
                Set.of(), List.of(), List.of(), "  "));
    }

    @Test
    void rejectsNegativeScore() {
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, -1, SelectorsContentReviewTier.NONE,
                Set.of(), List.of(), List.of(), "v1"));
    }

    @Test
    void confirmedRequiresDecisiveEvidenceAndNoneTier() {
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.CONFIRMED, 10, SelectorsContentReviewTier.NONE,
                Set.of(SelectorsContentEvidence.SELECTORS_NAME), List.of(), List.of(), "v1"));
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.CONFIRMED, 10, SelectorsContentReviewTier.NORMAL,
                Set.of(SelectorsContentEvidence.REFERRAL_CODE), List.of(), List.of(), "v1"));
    }

    @Test
    void reviewRequiredNeedsScoreAndMatchingTier() {
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.REVIEW_REQUIRED, 2, SelectorsContentReviewTier.NORMAL,
                Set.of(), List.of(), List.of(), "v1"));
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.REVIEW_REQUIRED, 3, SelectorsContentReviewTier.STRONG,
                Set.of(), List.of(), List.of(), "v1"));
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.REVIEW_REQUIRED, 6, SelectorsContentReviewTier.NORMAL,
                Set.of(), List.of(), List.of(), "v1"));
    }

    @Test
    void nonSelectorsCannotExceedScoreTwo() {
        assertThrows(IllegalArgumentException.class, () -> new SelectorsContentClassification(
                SelectorsContentDecision.NOT_SELECTORS, 3, SelectorsContentReviewTier.NONE,
                Set.of(), List.of(), List.of(), "v1"));
    }
}
