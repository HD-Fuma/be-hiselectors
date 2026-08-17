package com.fuma.hiselectors.content.classifier;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("링크 없이 레퍼럴 코드만 있으면 셀렉터스 콘텐츠로 판단하지 않는다")
    void referralCodeWithoutLink() {
        assertThat(classifier.isSelectorsContent(
                content("추천 코드 RC000003200T를 확인해 주세요"))).isFalse();
    }

    @Test
    @DisplayName("셀렉터스 상품 URL이 있으면 셀렉터스 콘텐츠로 판단한다")
    void productUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=RC000003200T")))
                .isTrue();
    }

    @Test
    @DisplayName("URL 인코딩된 추천 코드는 판단하지 않는다")
    void encodedProductUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=RC000003200%54")))
                .isFalse();
    }

    @Test
    @DisplayName("다른 셀렉터스의 추천 코드가 있는 상품 URL도 판단한다")
    void productUrlWithOtherReferralCode() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=RC999999999T")))
                .isTrue();
    }

    @Test
    @DisplayName("RC 형식이 아닌 추천 코드가 있는 상품 URL은 판단하지 않는다")
    void productUrlWithInvalidReferralCode() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=hello")))
                .isFalse();
    }

    @Test
    @DisplayName("소문자 rc로 시작하는 추천 코드가 있는 상품 URL도 판단한다")
    void productUrlWithLowercaseReferralCode() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=rc000003200t")))
                .isTrue();
    }

    @Test
    @DisplayName("추천 코드가 없는 상품 URL은 판단하지 않는다")
    void productUrlWithoutReferralCode() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341")))
                .isFalse();
    }

    @Test
    @DisplayName("추천 코드가 비어 있는 상품 URL은 판단하지 않는다")
    void productUrlWithBlankReferralCode() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=%20")))
                .isFalse();
    }

    @Test
    @DisplayName("레퍼런스 코드로 끝나는 샵 URL은 판단한다")
    void shopUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/sellectors/manage/shop/RC999999999T")))
                .isTrue();
    }

    @Test
    @DisplayName("RC 형식이 아닌 코드가 있는 샵 URL은 판단하지 않는다")
    void shopUrlWithInvalidReferralCode() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/sellectors/manage/shop/hello")))
                .isFalse();
    }

    @Test
    @DisplayName("상품 그룹 URL이 있으면 셀렉터스 콘텐츠로 판단한다")
    void productGroupUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/sellectors/manage/shop/RC999999999T/1")))
                .isTrue();
    }

    @Test
    @DisplayName("레퍼런스 코드가 없는 샵 URL은 판단하지 않는다")
    void shopUrlWithoutReferralCode() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/sellectors/manage/shop/")))
                .isFalse();
    }

    @Test
    @DisplayName("더현대가 아닌 호스트의 URL은 판단하지 않는다")
    void otherHostUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://evilhi.thehyundai.com/product/60A2099341?ptrsRefCd=RC000003200T")))
                .isFalse();
    }

    @Test
    @DisplayName("더현대와 셀렉터스 지정 키워드가 모두 있으면 판단한다")
    void designatedKeywords() {
        assertThat(classifier.isSelectorsContent(
                content("더현대Hi 셀렉터스 여름 추천"))).isTrue();
    }

    @Test
    @DisplayName("지정 키워드가 다른 문자열 안에 포함되어 있어도 판단한다")
    void embeddedDesignatedKeywords() {
        assertThat(classifier.isSelectorsContent(
                content("더현대Hi 셀렉터스몰 여름 추천"))).isTrue();
    }

    @Test
    @DisplayName("여러 TEXT에 지정 키워드가 나뉘어 있어도 판단한다")
    void designatedKeywordsAcrossTexts() {
        assertThat(classifier.isSelectorsContent(
                content(List.of("더현대Hi 여름 추천", "셀렉터스 활동"))))
                .isTrue();
    }

    @Test
    @DisplayName("지정 키워드 중 하나만 있으면 판단하지 않는다")
    void singleDesignatedKeyword() {
        assertThat(classifier.isSelectorsContent(
                content("더현대Hi 여름 추천"))).isFalse();
    }

    @Test
    @DisplayName("URL과 지정 키워드 조건이 없으면 셀렉터스 콘텐츠가 아니다")
    void unrelatedContent() {
        assertThat(classifier.isSelectorsContent(
                content("일반 게시글입니다"))).isFalse();
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
