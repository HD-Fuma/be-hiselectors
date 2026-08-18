package com.fuma.hiselectors.content.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectorsTextEvidenceExtractorTest {

    @Test
    void extractsStandaloneReferralCodesCaseInsensitivelyAndDeduplicatesThem() {
        SelectorsTextEvidenceExtractor.Result result = extract(
                "rc000005105t RC000005105T, RC000005106T");

        assertThat(result.referralCodes()).containsExactly("RC000005105T", "RC000005106T");
        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.REFERRAL_CODE);
        assertThat(result.score()).isZero();
    }

    @Test
    void acceptsPunctuationBoundariesButRejectsUnicodeWordAdjacencyAndMalformedCodes() {
        String valid = "(RC000005105T) RC000005106T, RC000005107T\u2028RC000005108T";
        String invalid = "XRC000005109T RC000005110TY 가RC000005111T RC000005112T나"
                + " RC000005113T١ RC000005114T２ RC000005115T_"
                + " RC00000511T RC0000051015T";

        SelectorsTextEvidenceExtractor.Result result = extract(valid + " " + invalid);

        assertThat(result.referralCodes()).containsExactly(
                "RC000005105T", "RC000005106T", "RC000005107T", "RC000005108T");
    }

    @Test
    void rejectsSupplementaryPlaneUnicodeLetterAdjacencyOnEitherSide() {
        String astralLetter = "\uD801\uDC00";

        SelectorsTextEvidenceExtractor.Result result = extract(
                astralLetter + "RC000005105T RC000005106T" + astralLetter);

        assertThat(result.referralCodes()).isEmpty();
    }

    @Test
    void doesNotPromoteReferralCodeInsideAnUntrustedUrlAfterUrlMasking() {
        String fullText = "https://evil.example/RC000005105T #셀렉터스";
        SelectorsUrlEvidenceExtractor.Result urls = SelectorsUrlEvidenceExtractor.extract(fullText);

        SelectorsTextEvidenceExtractor.Result result = SelectorsTextEvidenceExtractor.extract(
                fullText, urls.textWithoutUrls(), urls.evidence());

        assertThat(result.referralCodes()).isEmpty();
        assertThat(result.evidence()).doesNotContain(SelectorsContentEvidence.REFERRAL_CODE);
        assertThat(result.hashtags()).containsExactly("#셀렉터스");
    }

    @Test
    void doesNotPromoteHashtagPairWhenTheFirstHashtagIsInsideAnUntrustedUrl() {
        String fullText = "https://evil.example/#더현대서울 #셀렉터스";
        SelectorsUrlEvidenceExtractor.Result urls = SelectorsUrlEvidenceExtractor.extract(fullText);

        SelectorsTextEvidenceExtractor.Result result = SelectorsTextEvidenceExtractor.extract(
                fullText, urls.textWithoutUrls(), urls.evidence());

        assertThat(result.hashtags()).containsExactly("#셀렉터스");
        assertThat(result.evidence()).doesNotContain(
                SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR);
    }

    @Test
    void extractsDesignatedHashtagPairFromSeparateActualTokens() {
        for (List<String> expected : List.of(
                List.of("#더현대서울", "#셀렉터스"),
                List.of("#더현대", "#셀렉터스"))) {
            String text = String.join(" ", expected);
            SelectorsTextEvidenceExtractor.Result result = extract(text);

            assertThat(result.hashtags()).containsExactlyElementsOf(expected);
            assertThat(result.evidence()).containsExactly(
                    SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR,
                    SelectorsContentEvidence.SELECTORS_NAME,
                    SelectorsContentEvidence.THE_HYUNDAI_MENTION);
            assertThat(result.score()).isEqualTo(5);
        }
    }

    @Test
    void recognizesStandaloneSelectorsHashtagsCaseInsensitively() {
        for (String text : List.of("#셀렉터스", "#Selectors")) {
            SelectorsTextEvidenceExtractor.Result result = extract(text);

            assertThat(result.evidence()).as(text).containsExactly(
                    SelectorsContentEvidence.SELECTORS_NAME);
            assertThat(result.score()).as(text).isEqualTo(4);
            assertThat(result.evidence()).doesNotContain(
                    SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR);
        }
    }

    @Test
    void rejectsPlainTextIncompleteAndCombinedHashtagPairs() {
        for (String text : List.of(
                "더현대 셀렉터스",
                "#더현대서울",
                "#셀렉터스",
                "#더현대서울 #셀렉터스몰",
                "#SelectorsMall",
                "#더현대셀렉터스")) {
            SelectorsTextEvidenceExtractor.Result result = extract(text);

            assertThat(result.evidence()).doesNotContain(
                    SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR);
        }
    }

    @Test
    void scoresSelectorsBrandPhraseAndTheHyundaiOnce() {
        SelectorsTextEvidenceExtractor.Result result = extract("더현대 셀렉터스");

        assertThat(result.evidence()).containsExactly(
                SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.score()).isEqualTo(6);
    }

    @Test
    void recognizesCombinedSelectorsHashtagAsBrandPhraseWithoutHashtagPair() {
        SelectorsTextEvidenceExtractor.Result result = extract("#더현대셀렉터스");

        assertThat(result.evidence()).containsExactly(
                SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.evidence()).doesNotContain(
                SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR);
        assertThat(result.score()).isEqualTo(6);
    }

    @Test
    void scoresSelectorsShopNameAsOneNameFamilySignal() {
        SelectorsTextEvidenceExtractor.Result result = extract("셀렉터스 샵");

        assertThat(result.evidence()).containsExactly(
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.SELECTORS_SHOP_NAME);
        assertThat(result.score()).isEqualTo(4);
    }

    @Test
    void recognizesStandaloneSelectorsEnglishNameCaseInsensitively() {
        SelectorsTextEvidenceExtractor.Result result = extract("my SELECTORS");

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.SELECTORS_NAME);
        assertThat(result.score()).isEqualTo(4);
    }

    @Test
    void rejectsSelectorsNameSubstringsAndUnicodeWordAdjacency() {
        String astralLetter = "\uD801\uDC00";
        for (String text : List.of(
                "셀렉터스몰",
                "MySelectors",
                "SelectorsMall",
                "#셀렉터스몰",
                "#SelectorsMall",
                astralLetter + "Selectors",
                "Selectors" + astralLetter)) {
            SelectorsTextEvidenceExtractor.Result result = extract(text);

            assertThat(result.evidence()).as(text).doesNotContain(
                    SelectorsContentEvidence.SELECTORS_NAME,
                    SelectorsContentEvidence.SELECTORS_SHOP_NAME,
                    SelectorsContentEvidence.SELECTORS_BRAND_PHRASE);
            assertThat(result.score()).as(text).isZero();
        }
    }

    @Test
    void scoresIndependentSelectorsAndHyundaiSignals() {
        SelectorsTextEvidenceExtractor.Result result = extract("셀렉터스 그리고 더현대");

        assertThat(result.evidence()).containsExactly(
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.score()).isEqualTo(5);
    }

    @Test
    void recognizesEachTheHyundaiSignalOnce() {
        for (String text : List.of("더현대", "현대백화점", "THE HYUNDAI", "#더현대서울")) {
            SelectorsTextEvidenceExtractor.Result result = extract(text);

            assertThat(result.evidence()).as(text).containsExactly(
                    SelectorsContentEvidence.THE_HYUNDAI_MENTION);
            assertThat(result.score()).as(text).isEqualTo(1);
        }
    }

    @Test
    void doesNotScoreNameSignalsFoundOnlyInsideUrls() {
        String fullText = "https://evil.example/더현대/셀렉터스?name=Selectors";
        SelectorsUrlEvidenceExtractor.Result urls = SelectorsUrlEvidenceExtractor.extract(fullText);

        SelectorsTextEvidenceExtractor.Result result = SelectorsTextEvidenceExtractor.extract(
                fullText, urls.textWithoutUrls(), urls.evidence());

        assertThat(result.evidence()).isEmpty();
        assertThat(result.score()).isZero();
    }

    @Test
    void carriesSuppliedUrlEvidenceAndReturnsIndependentDeterministicImmutableCopies() {
        Set<SelectorsContentEvidence> suppliedEvidence = EnumSet.of(
                SelectorsContentEvidence.PUBLIC_PRODUCT_URL);
        Set<String> suppliedReferralCodes = new HashSet<>(Set.of("RC000005106T"));
        Set<String> suppliedHashtags = new HashSet<>(Set.of("#셀렉터스", "#더현대"));

        SelectorsTextEvidenceExtractor.Result extracted = SelectorsTextEvidenceExtractor.extract(
                "RC000005105T", "RC000005105T", suppliedEvidence);
        assertThat(extracted.evidence()).containsExactlyInAnyOrder(
                SelectorsContentEvidence.PUBLIC_PRODUCT_URL,
                SelectorsContentEvidence.REFERRAL_CODE);

        SelectorsTextEvidenceExtractor.Result result = new SelectorsTextEvidenceExtractor.Result(
                suppliedEvidence, suppliedReferralCodes, 0, suppliedHashtags);
        suppliedEvidence.clear();
        suppliedReferralCodes.clear();
        suppliedHashtags.clear();

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.PUBLIC_PRODUCT_URL);
        assertThat(result.referralCodes()).containsExactly("RC000005106T");
        assertThat(result.hashtags()).containsExactly("#더현대", "#셀렉터스");
        assertThatThrownBy(() -> result.evidence().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.referralCodes().add("RC000005105T"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.hashtags().add("#추가"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullInputsAndNegativeScores() {
        assertThatThrownBy(() -> SelectorsTextEvidenceExtractor.extract(
                null, "masked", Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SelectorsTextEvidenceExtractor.extract(
                "full", null, Set.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SelectorsTextEvidenceExtractor.extract(
                "full", "masked", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SelectorsTextEvidenceExtractor.Result(
                Set.of(), Set.of(), -1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SelectorsTextEvidenceExtractor.Result extract(String text) {
        return SelectorsTextEvidenceExtractor.extract(text, text, Set.of());
    }
}
