package com.fuma.hiselectors.content.classifier;

import com.fuma.hiselectors.content.client.dto.RawContent;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** 셀렉터스 콘텐츠 여부 판별 */
@Component
public class SelectorsContentClassifier {

    private static final String RULE_VERSION = "selectors-text-v1";
    private static final Set<SelectorsContentEvidence> DECISIVE_EVIDENCE = EnumSet.of(
            SelectorsContentEvidence.REFERRAL_CODE,
            SelectorsContentEvidence.PRODUCT_URL_WITH_REFERRAL,
            SelectorsContentEvidence.SELECTORS_SHOP_URL,
            SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR);

    public SelectorsContentClassification classify(RawContent rawContent) {
        if (rawContent == null) {
            throw new IllegalArgumentException("rawContent must not be null");
        }

        String normalizedText = Normalizer.normalize(rawContent.caption(), Normalizer.Form.NFKC);
        SelectorsUrlEvidenceExtractor.Result urlResult = SelectorsUrlEvidenceExtractor.extract(normalizedText);
        SelectorsTextEvidenceExtractor.Result textResult = SelectorsTextEvidenceExtractor.extract(
                normalizedText, urlResult.textWithoutUrls(), urlResult.evidence());

        EnumSet<SelectorsContentEvidence> evidence = urlResult.evidence().isEmpty()
                ? EnumSet.noneOf(SelectorsContentEvidence.class)
                : EnumSet.copyOf(urlResult.evidence());
        evidence.addAll(textResult.evidence());

        TreeSet<String> referralCodes = new TreeSet<>(urlResult.referralCodes());
        referralCodes.addAll(textResult.referralCodes());
        int score = textResult.score()
                + (evidence.contains(SelectorsContentEvidence.PUBLIC_PRODUCT_URL) ? 3 : 0);

        return new SelectorsContentClassification(
                decisionFor(evidence, score),
                score,
                reviewTierFor(evidence, score),
                evidence,
                List.copyOf(referralCodes),
                urlResult.matchedUrls(),
                RULE_VERSION);
    }

    public boolean isSelectorsContent(RawContent rawContent) {
        return classify(rawContent).decision() == SelectorsContentDecision.CONFIRMED;
    }

    private SelectorsContentDecision decisionFor(
            Set<SelectorsContentEvidence> evidence, int score) {
        if (DECISIVE_EVIDENCE.stream().anyMatch(evidence::contains)) {
            return SelectorsContentDecision.CONFIRMED;
        }
        if (score >= 3) {
            return SelectorsContentDecision.REVIEW_REQUIRED;
        }
        return SelectorsContentDecision.NOT_SELECTORS;
    }

    private SelectorsContentReviewTier reviewTierFor(
            Set<SelectorsContentEvidence> evidence, int score) {
        if (DECISIVE_EVIDENCE.stream().anyMatch(evidence::contains)) {
            return SelectorsContentReviewTier.NONE;
        }
        if (score >= 6) {
            return SelectorsContentReviewTier.STRONG;
        }
        if (score >= 3) {
            return SelectorsContentReviewTier.NORMAL;
        }
        return SelectorsContentReviewTier.NONE;
    }
}
