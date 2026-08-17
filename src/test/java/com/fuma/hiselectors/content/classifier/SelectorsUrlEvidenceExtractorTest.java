package com.fuma.hiselectors.content.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectorsUrlEvidenceExtractorTest {

    @Test
    void extractsDistinctSortedUrlsAndMasksTheirText() {
        String text = "앞 https://hi.thehyundai.com/product/A... 뒤\n"
                + "https://hi.thehyundai.com/product/A\n"
                + "https://hi.thehyundai.com/product/B)]";

        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(text);

        assertThat(result.matchedUrls()).containsExactly(
                "https://hi.thehyundai.com/product/A",
                "https://hi.thehyundai.com/product/B");
        assertThat(result.textWithoutUrls()).isEqualTo(
                "앞 " + " ".repeat("https://hi.thehyundai.com/product/A".length()) + "... 뒤\n"
                        + " ".repeat("https://hi.thehyundai.com/product/A".length()) + "\n"
                        + " ".repeat("https://hi.thehyundai.com/product/B".length()) + ")]" );
    }

    @Test
    void trustsOnlyTheExactHostAndDefaultPorts() {
        String text = "https://hi.thehyundai.com/a https://hi.thehyundai.com:80/b "
                + "http://HI.THEHYUNDAI.COM/c http://hi.thehyundai.com:80/i https://hi.thehyundai.com:443/d "
                + "https://user@hi.thehyundai.com/e https://hi.thehyundai.com:8080/f "
                + "https://evilhi.thehyundai.com/g https://sub.hi.thehyundai.com/h "
                + "https://[bad https://hi.thehyundai.com:/empty-port https://hi.thehyundai.com/valid "
                + "ftp://hi.thehyundai.com/nope";

        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(text);

        assertThat(result.trustedUrls()).containsExactly(
                "http://HI.THEHYUNDAI.COM/c",
                "http://hi.thehyundai.com:80/i",
                "https://hi.thehyundai.com/a",
                "https://hi.thehyundai.com/valid",
                "https://hi.thehyundai.com:443/d");
        assertThat(result.matchedUrls()).contains(
                "https://[bad",
                "https://user@hi.thehyundai.com/e",
                "https://hi.thehyundai.com:8080/f",
                "https://evilhi.thehyundai.com/g",
                "https://sub.hi.thehyundai.com/h",
                "https://hi.thehyundai.com/valid");
        assertThat(result.matchedUrls()).contains("https://hi.thehyundai.com:/empty-port");
        assertThat(result.matchedUrls()).noneMatch(url -> url.startsWith("ftp://"));
    }

    @Test
    void classifiesTrustedProductPath() {
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "https://hi.thehyundai.com/product/A_1-2/");

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.PUBLIC_PRODUCT_URL);
    }

    @Test
    void classifiesTrustedSelectorsShopAndGroupPaths() {
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "https://hi.thehyundai.com/sellectors/manage/shop/rc000005105t "
                        + "https://hi.thehyundai.com/sellectors/manage/shop/RC000005105T/1/ "
                        + "https://hi.thehyundai.com/sellectors/67/");

        assertThat(result.evidence()).containsExactlyInAnyOrder(
                SelectorsContentEvidence.SELECTORS_SHOP_URL,
                SelectorsContentEvidence.REFERRAL_CODE);
        assertThat(result.referralCodes()).containsExactly("RC000005105T");
    }

    @Test
    void rejectsMalformedSelectorsShopAndGroupPaths() {
        String longId = "a".repeat(101);
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "https://hi.thehyundai.com/sellectors/manage/shop/ "
                        + "https://hi.thehyundai.com/sellectors/manage/shop/RC000005105T/2 "
                        + "https://hi.thehyundai.com/sellectors/manage/shop/RC000005105T/1/extra "
                        + "https://hi.thehyundai.com/sellectors/manage/shop/RC000005105%54 "
                        + "https://hi.thehyundai.com/sellectors/manage/shop/RC000005105TT "
                        + "https://hi.thehyundai.com/sellectors/manage/shop/RC000005105T// "
                        + "https://hi.thehyundai.com/sellectors/ "
                        + "https://hi.thehyundai.com/sellectors/../67 "
                        + "https://hi.thehyundai.com/sellectors/a%2Fb "
                        + "https://hi.thehyundai.com/sellectors/" + longId + " "
                        + "https://hi.thehyundai.com/sellectors/67//");

        assertThat(result.evidence()).isEmpty();
        assertThat(result.referralCodes()).isEmpty();
    }

    @Test
    void rejectsMalformedProductPaths() {
        String longId = "a".repeat(101);
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "https://hi.thehyundai.com/product/ https://hi.thehyundai.com/product/"
                        + longId + " https://hi.thehyundai.com/product/A/B "
                        + "https://hi.thehyundai.com/product/../A "
                        + "https://hi.thehyundai.com/product/A%2FB");

        assertThat(result.evidence()).doesNotContain(SelectorsContentEvidence.PUBLIC_PRODUCT_URL);
    }

    @Test
    void classifiesExactlyOneValidReferralQueryAndCanonicalizesCode() {
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "https://hi.thehyundai.com/product/A_1-2?PTRSREFCD=rc000005105t");

        assertThat(result.evidence()).containsExactlyInAnyOrder(
                SelectorsContentEvidence.PUBLIC_PRODUCT_URL,
                SelectorsContentEvidence.PRODUCT_URL_WITH_REFERRAL,
                SelectorsContentEvidence.REFERRAL_CODE);
        assertThat(result.referralCodes()).containsExactly("RC000005105T");
    }

    @Test
    void invalidOrDuplicateReferralKeepsOnlyPublicProductEvidence() {
        String text = "https://hi.thehyundai.com/product/A?foo=1 "
                + "https://hi.thehyundai.com/product/B?ptrsRefCd=bad "
                + "https://hi.thehyundai.com/product/C?ptrsRefCd= "
                + "https://hi.thehyundai.com/product/D?ptrsRefCd=RC000005105%54 "
                + "https://hi.thehyundai.com/product/E?ptrsRefCd=RC000005105T&PTRSREFCD=RC000005105T";

        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(text);

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.PUBLIC_PRODUCT_URL);
        assertThat(result.referralCodes()).isEmpty();
    }

    @Test
    void validReferralSurvivesUnrelatedEncodedQueryParameter() {
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "https://hi.thehyundai.com/product/A?x=%2F&ptrsRefCd=RC000005105T");

        assertThat(result.evidence()).contains(
                SelectorsContentEvidence.PUBLIC_PRODUCT_URL,
                SelectorsContentEvidence.PRODUCT_URL_WITH_REFERRAL,
                SelectorsContentEvidence.REFERRAL_CODE);
        assertThat(result.referralCodes()).containsExactly("RC000005105T");
    }

    @Test
    void extractsUrlImmediatelyFollowingAnAsciiWordCharacter() {
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "xhttps://evil.example/RC000005105T");

        assertThat(result.matchedUrls()).containsExactly("https://evil.example/RC000005105T");
        assertThat(result.textWithoutUrls()).isEqualTo("x" + " ".repeat(
                "https://evil.example/RC000005105T".length()));
        assertThat(result.textWithoutUrls()).doesNotContain("RC000005105T");
    }

    @Test
    void treatsUnicodeLineSeparatorsAsCandidateBoundaries() {
        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(
                "https://evil.example/a\u2028RC000005105T\u2029https://evil.example/b");

        assertThat(result.textWithoutUrls()).contains("RC000005105T");
        assertThat(result.matchedUrls()).containsExactly(
                "https://evil.example/a", "https://evil.example/b");
    }

    @Test
    void resultCollectionsAreIndependentImmutableCopies() {
        Set<SelectorsContentEvidence> evidence = EnumSet.of(SelectorsContentEvidence.SELECTORS_NAME);
        Set<String> referralCodes = new HashSet<>(Set.of("RC1"));
        List<String> matchedUrls = new ArrayList<>(List.of("https://b.example", "https://a.example"));
        List<String> trustedUrls = new ArrayList<>(List.of("https://a.example"));

        SelectorsUrlEvidenceExtractor.Result result = new SelectorsUrlEvidenceExtractor.Result(
                "masked", evidence, referralCodes, matchedUrls, trustedUrls);
        evidence.clear();
        referralCodes.clear();
        matchedUrls.clear();
        trustedUrls.clear();

        assertThat(result.evidence()).containsExactly(SelectorsContentEvidence.SELECTORS_NAME);
        assertThat(result.referralCodes()).containsExactly("RC1");
        assertThat(result.matchedUrls()).containsExactly("https://a.example", "https://b.example");
        assertThat(result.trustedUrls()).containsExactly("https://a.example");
        assertThatThrownBy(() -> result.matchedUrls().add("https://c.example"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
