package com.fuma.hiselectors.content.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelectorsContentClassifierTest {

    private final SelectorsContentClassifier classifier = new SelectorsContentClassifier();

    @Test
    @DisplayName("더현대 광고 표기는 낮은 점수의 비셀렉터스 콘텐츠다")
    void theHyundaiDisclosureIsNotSelectors() {
        assertClassification(
                classifier.classify(content("더현대 #광고")),
                SelectorsContentDecision.NOT_SELECTORS,
                2,
                SelectorsContentReviewTier.NONE,
                List.of(SelectorsContentEvidence.ECONOMIC_DISCLOSURE,
                        SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("더현대 광고와 구매 유도는 일반 검토 대상이다")
    void theHyundaiDisclosureAndPurchaseCtaRequiresNormalReview() {
        assertClassification(
                classifier.classify(content("더현대 #광고 프로필 링크")),
                SelectorsContentDecision.REVIEW_REQUIRED,
                3,
                SelectorsContentReviewTier.NORMAL,
                List.of(SelectorsContentEvidence.ECONOMIC_DISCLOSURE,
                        SelectorsContentEvidence.PURCHASE_CTA,
                        SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("셀렉터스와 더현대 언급은 일반 검토 대상이다")
    void selectorsNameAndTheHyundaiRequireNormalReview() {
        assertClassification(
                classifier.classify(content("셀렉터스 그리고 더현대")),
                SelectorsContentDecision.REVIEW_REQUIRED,
                5,
                SelectorsContentReviewTier.NORMAL,
                List.of(SelectorsContentEvidence.SELECTORS_NAME,
                        SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("더현대 셀렉터스 문구는 강한 검토 대상이며 확정은 아니다")
    void selectorsBrandPhraseRequiresStrongReview() {
        assertClassification(
                classifier.classify(content("더현대 셀렉터스")),
                SelectorsContentDecision.REVIEW_REQUIRED,
                6,
                SelectorsContentReviewTier.STRONG,
                List.of(SelectorsContentEvidence.SELECTORS_BRAND_PHRASE,
                        SelectorsContentEvidence.SELECTORS_NAME,
                        SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of());
        assertThat(classifier.isSelectorsContent(content("더현대 셀렉터스"))).isFalse();
    }

    @Test
    @DisplayName("공개 상품 URL은 한 번의 보정 점수로 강한 검토 대상이 된다")
    void publicProductUrlRequiresStrongReview() {
        String url = "https://hi.thehyundai.com/product/A";
        assertClassification(
                classifier.classify(content(url + " 더현대 #광고 구매하기")),
                SelectorsContentDecision.REVIEW_REQUIRED,
                6,
                SelectorsContentReviewTier.STRONG,
                List.of(SelectorsContentEvidence.PUBLIC_PRODUCT_URL,
                        SelectorsContentEvidence.ECONOMIC_DISCLOSURE,
                        SelectorsContentEvidence.PURCHASE_CTA,
                        SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of(url));
    }

    @Test
    @DisplayName("여러 공개 상품 URL은 보정 점수를 한 번만 더하고 정리된 URL을 보존한다")
    void multiplePublicProductUrlsAddSoftScoreOnlyOnce() {
        String firstUrl = "https://hi.thehyundai.com/product/A";
        String secondUrl = "https://hi.thehyundai.com/product/B";
        assertClassification(
                classifier.classify(content(secondUrl + " " + firstUrl + " " + secondUrl)),
                SelectorsContentDecision.REVIEW_REQUIRED,
                3,
                SelectorsContentReviewTier.NORMAL,
                List.of(SelectorsContentEvidence.PUBLIC_PRODUCT_URL),
                List.of(),
                List.of(firstUrl, secondUrl));
    }

    @Test
    @DisplayName("레퍼럴 상품 URL은 확정하고 URL 코드를 정규화한다")
    void productUrlWithReferralIsConfirmed() {
        String url = "https://hi.thehyundai.com/product/A?ptrsRefCd=rc000005105t";
        assertClassification(
                classifier.classify(content(url)),
                SelectorsContentDecision.CONFIRMED,
                3,
                SelectorsContentReviewTier.NONE,
                List.of(SelectorsContentEvidence.REFERRAL_CODE,
                        SelectorsContentEvidence.PRODUCT_URL_WITH_REFERRAL,
                        SelectorsContentEvidence.PUBLIC_PRODUCT_URL),
                List.of("RC000005105T"),
                List.of(url));
    }

    @Test
    @DisplayName("단어 토큰에 붙은 URL 부분 문자열은 마스킹하되 확정 근거로 쓰지 않는다")
    void embeddedProductUrlSubstringDoesNotConfirm() {
        String url = "https://hi.thehyundai.com/product/A?ptrsRefCd=RC000005105T";

        assertClassification(
                classifier.classify(content("x" + url)),
                SelectorsContentDecision.NOT_SELECTORS,
                0,
                SelectorsContentReviewTier.NONE,
                List.of(),
                List.of(),
                List.of(url));
    }

    @Test
    @DisplayName("NFKC 정규화 뒤에도 유니코드 숫자에 붙은 URL은 확정 근거로 쓰지 않는다")
    void unicodeNumberPrefixedProductUrlsDoNotConfirmAfterNormalization() {
        String url = "https://hi.thehyundai.com/product/A?ptrsRefCd=RC000005105T";

        for (String prefix : List.of("ᛮ", "৴")) {
            assertClassification(
                    classifier.classify(content(prefix + url)),
                    SelectorsContentDecision.NOT_SELECTORS,
                    0,
                    SelectorsContentReviewTier.NONE,
                    List.of(),
                    List.of(),
                    List.of(url));
        }
    }

    @Test
    @DisplayName("전각 레퍼럴 코드는 NFKC 정규화 후 확정한다")
    void normalizesFullWidthReferralCode() {
        assertClassification(
                classifier.classify(content("ＲＣ０００００５１０５Ｔ")),
                SelectorsContentDecision.CONFIRMED,
                0,
                SelectorsContentReviewTier.NONE,
                List.of(SelectorsContentEvidence.REFERRAL_CODE),
                List.of("RC000005105T"),
                List.of());
    }

    @Test
    @DisplayName("여러 원문 TEXT의 신호를 함께 조합한다")
    void combinesSignalsAcrossRawContentTexts() {
        assertClassification(
                classifier.classify(content(List.of("셀렉터스", "더현대 #광고 프로필 링크"))),
                SelectorsContentDecision.REVIEW_REQUIRED,
                7,
                SelectorsContentReviewTier.STRONG,
                List.of(SelectorsContentEvidence.SELECTORS_NAME,
                        SelectorsContentEvidence.ECONOMIC_DISCLOSURE,
                        SelectorsContentEvidence.PURCHASE_CTA,
                        SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("null 원문은 명확한 인수 예외를 던진다")
    void rejectsNullRawContent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> classifier.classify(null))
                .withMessage("rawContent must not be null");
    }

    @Test
    @DisplayName("빈 TEXT 목록은 근거 없는 비셀렉터스 콘텐츠다")
    void emptyTextsAreNotSelectors() {
        assertClassification(
                classifier.classify(content(List.of())),
                SelectorsContentDecision.NOT_SELECTORS,
                0,
                SelectorsContentReviewTier.NONE,
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("셀렉터스몰은 셀렉터스 독립 명칭으로 판단하지 않는다")
    void selectorsMallIsNotSelectorsName() {
        assertClassification(
                classifier.classify(content("더현대 셀렉터스몰")),
                SelectorsContentDecision.NOT_SELECTORS,
                1,
                SelectorsContentReviewTier.NONE,
                List.of(SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("신뢰되지 않은 URL도 정리된 후보 URL 목록에는 남긴다")
    void preservesUntrustedMatchedUrl() {
        String url = "https://evilhi.thehyundai.com/product/A,";
        assertClassification(
                classifier.classify(content(url)),
                SelectorsContentDecision.NOT_SELECTORS,
                0,
                SelectorsContentReviewTier.NONE,
                List.of(),
                List.of(),
                List.of("https://evilhi.thehyundai.com/product/A"));
    }

    @Test
    @DisplayName("호환 API는 확정된 콘텐츠에만 true를 반환한다")
    void compatibilityApiReturnsTrueOnlyForConfirmedContent() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/A?ptrsRefCd=RC000005105T"))).isTrue();
        assertThat(classifier.isSelectorsContent(content("더현대 셀렉터스"))).isFalse();
        assertThat(classifier.isSelectorsContent(content("일반 게시글입니다"))).isFalse();
    }

    @Test
    @DisplayName("지정 해시태그 쌍과 셀렉터스 샵 URL은 호환 API에서 확정된다")
    void hardHashtagPairAndShopUrlsRemainConfirmed() {
        assertClassification(
                classifier.classify(content("#더현대 #셀렉터스")),
                SelectorsContentDecision.CONFIRMED,
                5,
                SelectorsContentReviewTier.NONE,
                List.of(SelectorsContentEvidence.DESIGNATED_HASHTAG_PAIR,
                        SelectorsContentEvidence.SELECTORS_NAME,
                        SelectorsContentEvidence.THE_HYUNDAI_MENTION),
                List.of(),
                List.of());
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/sellectors/manage/shop/RC999999999T"))).isTrue();
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/sellectors/group-a"))).isTrue();
    }

    @Test
    @DisplayName("유효하지 않거나 공유된 상품 URL은 결정적 근거 없이 확정하지 않는다")
    void invalidOrSharedProductUrlsDoNotHardConfirm() {
        String invalid = "https://hi.thehyundai.com/product/A?ptrsRefCd=hello";
        String shared = "https://hi.thehyundai.com/product/A?ptrsRefCd=RC000005105T&ptrsRefCd=RC000005106T";
        assertClassification(
                classifier.classify(content(invalid)),
                SelectorsContentDecision.REVIEW_REQUIRED,
                3,
                SelectorsContentReviewTier.NORMAL,
                List.of(SelectorsContentEvidence.PUBLIC_PRODUCT_URL),
                List.of(),
                List.of(invalid));
        assertClassification(
                classifier.classify(content(shared)),
                SelectorsContentDecision.REVIEW_REQUIRED,
                3,
                SelectorsContentReviewTier.NORMAL,
                List.of(SelectorsContentEvidence.PUBLIC_PRODUCT_URL),
                List.of(),
                List.of(shared));
    }

    private void assertClassification(
            SelectorsContentClassification actual,
            SelectorsContentDecision decision,
            int score,
            SelectorsContentReviewTier reviewTier,
            List<SelectorsContentEvidence> evidence,
            List<String> referralCodes,
            List<String> matchedUrls) {
        assertThat(actual.decision()).isEqualTo(decision);
        assertThat(actual.score()).isEqualTo(score);
        assertThat(actual.reviewTier()).isEqualTo(reviewTier);
        assertThat(actual.evidence()).containsExactlyElementsOf(evidence);
        assertThat(actual.referralCodes()).containsExactlyElementsOf(referralCodes);
        assertThat(actual.matchedUrls()).containsExactlyElementsOf(matchedUrls);
        assertThat(actual.ruleVersion()).isEqualTo("selectors-text-v1");
    }

    private RawContent content(String caption) {
        return content(List.of(caption));
    }

    private RawContent content(List<String> texts) {
        return new RawContent(
                SnsPlatform.INSTAGRAM,
                "content-id",
                "https://www.instagram.com/p/content-id",
                ContentType.FEED,
                texts,
                LocalDateTime.of(2026, 8, 13, 12, 0),
                List.of());
    }
}
