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

    private static final String SELECTORS_CODE = "RC000003200T";

    private final SelectorsContentClassifier classifier = new SelectorsContentClassifier();

    @Test
    @DisplayName("셀렉터스 레퍼럴 코드가 있으면 셀렉터스 콘텐츠로 판단한다")
    void referralCode() {
        assertThat(classifier.isSelectorsContent(
                content("추천 코드 RC000003200T를 확인해 주세요"), SELECTORS_CODE)).isTrue();
    }

    @Test
    @DisplayName("셀렉터스 상품 URL이 있으면 셀렉터스 콘텐츠로 판단한다")
    void productUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=RC000003200T"),
                SELECTORS_CODE)).isTrue();
    }

    @Test
    @DisplayName("다른 셀렉터스의 상품 URL은 판단하지 않는다")
    void otherSelectorsProductUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/product/60A2099341?ptrsRefCd=RC999999999T"),
                SELECTORS_CODE)).isFalse();
    }

    @Test
    @DisplayName("상품 그룹 URL이 있으면 셀렉터스 콘텐츠로 판단한다")
    void productGroupUrl() {
        assertThat(classifier.isSelectorsContent(content(
                "https://hi.thehyundai.com/sellectors/manage/shop/RC000003200T/1"),
                SELECTORS_CODE)).isTrue();
    }

    @Test
    @DisplayName("더현대와 셀렉터스 지정 키워드가 모두 있으면 판단한다")
    void designatedKeywords() {
        assertThat(classifier.isSelectorsContent(
                content("더현대Hi 셀렉터스 여름 추천"), SELECTORS_CODE)).isTrue();
    }

    @Test
    @DisplayName("여러 TEXT에 지정 키워드가 나뉘어 있어도 판단한다")
    void designatedKeywordsAcrossTexts() {
        assertThat(classifier.isSelectorsContent(
                content(List.of("더현대Hi 여름 추천", "셀렉터스 활동")),
                SELECTORS_CODE)).isTrue();
    }

    @Test
    @DisplayName("지정 키워드 중 하나만 있으면 판단하지 않는다")
    void singleDesignatedKeyword() {
        assertThat(classifier.isSelectorsContent(
                content("더현대Hi 여름 추천"), SELECTORS_CODE)).isFalse();
    }

    @Test
    @DisplayName("네 가지 조건이 없으면 셀렉터스 콘텐츠가 아니다")
    void unrelatedContent() {
        assertThat(classifier.isSelectorsContent(
                content("일반 게시글입니다"), SELECTORS_CODE)).isFalse();
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
