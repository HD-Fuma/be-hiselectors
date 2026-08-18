package com.fuma.hiselectors.content.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {
            "#광고", "#협찬", "#ad", "유료광고", "유료 광고", "광고입니다",
            "협찬받아", "제휴 링크", "판매 수수료", "paid partnership", "PAID PARTNERSHIP", "paid link"
    })
    void scoresEachEconomicDisclosureSignalExactlyOnce(String signal) {
        SelectorsTextEvidenceExtractor.Result result = extract(signal);

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.ECONOMIC_DISCLOSURE);
        assertThat(result.score()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"#광고주", "#협찬사", "#advice"})
    void rejectsNonDisclosureHashtags(String hashtag) {
        SelectorsTextEvidenceExtractor.Result result = extract(hashtag);

        assertThat(result.evidence()).isEmpty();
        assertThat(result.score()).isZero();
    }

    @Test
    void scoresRepeatedEconomicDisclosureTermsOnce() {
        SelectorsTextEvidenceExtractor.Result result = extract(
                "#광고 유료광고 광고입니다 paid partnership #ad");

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.ECONOMIC_DISCLOSURE);
        assertThat(result.score()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"협찬받아서", "오늘유료광고예요"})
    void scoresEconomicDisclosureLiteralInsideNormalizedText(String text) {
        SelectorsTextEvidenceExtractor.Result result = extract(text);

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.ECONOMIC_DISCLOSURE);
        assertThat(result.score()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "프로필 링크", "링크 확인", "링크 클릭", "구매하기", "지금 구매", "바로 구매",
            "구매 링크", "주문하기", "지금 주문", "예약하기", "쿠폰", "할인 코드", "DM 문의"
    })
    void scoresEachPurchaseCtaSignalExactlyOnce(String signal) {
        SelectorsTextEvidenceExtractor.Result result = extract(signal);

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.PURCHASE_CTA);
        assertThat(result.score()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"구매력", "주문진", "예약어"})
    void rejectsPurchaseCtaSubstrings(String text) {
        SelectorsTextEvidenceExtractor.Result result = extract(text);

        assertThat(result.evidence()).isEmpty();
        assertThat(result.score()).isZero();
    }

    @Test
    void scoresRepeatedPurchaseCtaTermsOnce() {
        SelectorsTextEvidenceExtractor.Result result = extract(
                "프로필 링크 링크 확인 구매하기 지금 구매 구매 링크 주문하기 쿠폰 DM 문의");

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.PURCHASE_CTA);
        assertThat(result.score()).isEqualTo(1);
    }

    @Test
    void scoresPurchaseCtaLiteralInsideNormalizedText() {
        SelectorsTextEvidenceExtractor.Result result = extract("쿠폰받기");

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.PURCHASE_CTA);
        assertThat(result.score()).isEqualTo(1);
    }

    @Test
    void composesEconomicDisclosureAndPurchaseCtaWithExistingSignals() {
        SelectorsTextEvidenceExtractor.Result result = extract(
                "더현대 셀렉터스 광고입니다 유료광고 프로필 링크 구매하기 지금 구매");

        assertThat(result.evidence()).containsExactlyInAnyOrder(
                SelectorsContentEvidence.ECONOMIC_DISCLOSURE,
                SelectorsContentEvidence.PURCHASE_CTA,
                SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.score()).isEqualTo(8);
    }

    @Test
    void composesEconomicDisclosureIndependentlyWithNameAndHyundaiSignals() {
        SelectorsTextEvidenceExtractor.Result result = extract("더현대 셀렉터스 광고입니다");

        assertThat(result.evidence()).containsExactlyInAnyOrder(
                SelectorsContentEvidence.ECONOMIC_DISCLOSURE,
                SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.score()).isEqualTo(7);
    }

    @Test
    void composesPurchaseCtaIndependentlyWithNameAndHyundaiSignals() {
        SelectorsTextEvidenceExtractor.Result result = extract("더현대 셀렉터스 쿠폰");

        assertThat(result.evidence()).containsExactlyInAnyOrder(
                SelectorsContentEvidence.PURCHASE_CTA,
                SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.score()).isEqualTo(7);
    }

    @Test
    void preservesHardUrlEvidenceWithoutScoringItAlongsideSoftSignals() {
        String fullText = "https://hi.thehyundai.com/product/sku?ptrsRefCd=RC000005105T 광고입니다 쿠폰";
        SelectorsUrlEvidenceExtractor.Result urls = SelectorsUrlEvidenceExtractor.extract(fullText);

        SelectorsTextEvidenceExtractor.Result result = SelectorsTextEvidenceExtractor.extract(
                fullText, urls.textWithoutUrls(), urls.evidence());

        assertThat(result.evidence()).containsExactlyInAnyOrder(
                SelectorsContentEvidence.PRODUCT_URL_WITH_REFERRAL,
                SelectorsContentEvidence.REFERRAL_CODE,
                SelectorsContentEvidence.PUBLIC_PRODUCT_URL,
                SelectorsContentEvidence.ECONOMIC_DISCLOSURE,
                SelectorsContentEvidence.PURCHASE_CTA);
        assertThat(result.score()).isEqualTo(2);
    }

    @Test
    void doesNotBridgeEconomicDisclosureOrPurchaseCtaAcrossMaskedSpans() {
        SelectorsTextEvidenceExtractor.Result disclosure = extractAfterUrlMasking(
                "유료 https://evil.example/x 광고");
        SelectorsTextEvidenceExtractor.Result cta = extractAfterUrlMasking(
                "프로필 #잡담 링크");

        assertThat(disclosure.evidence()).doesNotContain(SelectorsContentEvidence.ECONOMIC_DISCLOSURE);
        assertThat(disclosure.score()).isZero();
        assertThat(cta.evidence()).doesNotContain(SelectorsContentEvidence.PURCHASE_CTA);
        assertThat(cta.score()).isZero();
    }

    @Test
    void doesNotScoreEconomicDisclosureOrPurchaseCtaFoundOnlyInsideUrls() {
        String fullText = "https://evil.example/광고입니다 https://evil.example/구매하기";
        SelectorsUrlEvidenceExtractor.Result urls = SelectorsUrlEvidenceExtractor.extract(fullText);

        SelectorsTextEvidenceExtractor.Result result = SelectorsTextEvidenceExtractor.extract(
                fullText, urls.textWithoutUrls(), urls.evidence());

        assertThat(result.evidence()).isEmpty();
        assertThat(result.score()).isZero();
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
    void doesNotBridgeSelectorsBrandPhraseAcrossMaskedUrl() {
        String fullText = "더현대 https://evil.example/path 셀렉터스";
        SelectorsTextEvidenceExtractor.Result result = extractAfterUrlMasking(fullText);

        assertThat(result.evidence()).containsExactly(
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.evidence()).doesNotContain(SelectorsContentEvidence.SELECTORS_BRAND_PHRASE);
        assertThat(result.score()).isEqualTo(5);
    }

    @Test
    void doesNotBridgeSelectorsBrandPhraseAcrossMaskedHashtag() {
        SelectorsTextEvidenceExtractor.Result result = extractAfterUrlMasking(
                "더현대 #잡담 셀렉터스");

        assertThat(result.evidence()).containsExactly(
                SelectorsContentEvidence.SELECTORS_NAME,
                SelectorsContentEvidence.THE_HYUNDAI_MENTION);
        assertThat(result.evidence()).doesNotContain(SelectorsContentEvidence.SELECTORS_BRAND_PHRASE);
        assertThat(result.score()).isEqualTo(5);
    }

    @Test
    void doesNotBridgeTheHyundaiPhraseAcrossMaskedHashtag() {
        SelectorsTextEvidenceExtractor.Result result = extractAfterUrlMasking(
                "THE #잡담 HYUNDAI");

        assertThat(result.evidence()).isEmpty();
        assertThat(result.score()).isZero();
    }

    @Test
    void doesNotBridgeSelectorsShopNameAcrossMaskedUrl() {
        SelectorsTextEvidenceExtractor.Result result = extractAfterUrlMasking(
                "셀렉터스 https://evil.example/path 샵");

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.SELECTORS_NAME);
        assertThat(result.evidence()).doesNotContain(SelectorsContentEvidence.SELECTORS_SHOP_NAME);
        assertThat(result.score()).isEqualTo(4);
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

    private static SelectorsTextEvidenceExtractor.Result extractAfterUrlMasking(String fullText) {
        SelectorsUrlEvidenceExtractor.Result urls = SelectorsUrlEvidenceExtractor.extract(fullText);
        return SelectorsTextEvidenceExtractor.extract(fullText, urls.textWithoutUrls(), urls.evidence());
    }
}
