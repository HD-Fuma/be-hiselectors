package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.creator.discovery.IgHandleExtractor.IgHandle;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 인스타 핸들 추출 검증.
 *
 * <p>테스트 데이터 상당수는 실제 발굴(뷰티 키워드 22개 채널)에서 가져온 채널 설명이다.
 * 목 데이터로 만든 패턴과 실제 표기가 꽤 달랐기 때문이다.
 */
class IgHandleExtractorTest {

    private final IgHandleExtractor extractor = new IgHandleExtractor();

    @Test
    @DisplayName("실측 - Instagram 다음 줄에 @핸들")
    void realCaseNewlineAfterLabel() {
        Optional<IgHandle> result = extractor.extract("""
                Instagram
                @yeonlechexe

                Business
                • business@ahnnkglobal.kr
                • keepgoingyeon@naver.com (개인)
                """);

        assertThat(result).isPresent();
        assertThat(result.get().handle()).isEqualTo("yeonlechexe");
        assertThat(result.get().source()).isEqualTo(IgHandleSource.LABELED);
    }

    @Test
    @DisplayName("실측 - 콜론 앞뒤로 공백이 여러 개")
    void realCaseSpacedColon() {
        Optional<IgHandle> result = extractor.extract("""
                누구인가? 누가 구독을 안했는가?

                instagram  :  sawakoko13
                """);

        assertThat(result).isPresent();
        assertThat(result.get().handle()).isEqualTo("sawakoko13");
    }

    @Test
    @DisplayName("실측 - 대괄호 안에 Insta:@핸들")
    void realCaseBracketed() {
        Optional<IgHandle> result = extractor.extract(
                "혼자 노는게 제일 좋은 인간의 일상 [Insta:@nana_auau]\n\n• 문의: nana@sandboxnetwork.net");

        assertThat(result).isPresent();
        assertThat(result.get().handle()).isEqualTo("nana_auau");
    }

    @Test
    @DisplayName("실측 - @ 없이 약어 뒤에 핸들만")
    void realCaseAbbreviationWithoutAt() {
        Optional<IgHandle> result = extractor.extract("Insta smokinghotvirgin");

        assertThat(result).isPresent();
        assertThat(result.get().handle()).isEqualTo("smokinghotvirgin");
    }

    @Test
    @DisplayName("실측 - 이메일 사이에 낀 단독 @멘션은 신뢰도가 낮다")
    void realCaseBareMention() {
        Optional<IgHandle> result = extractor.extract("""
                hanchuchus2@gmail.com

                @hxnjxxu

                contact: ask@fullhousekorea.com
                """);

        assertThat(result).isPresent();
        assertThat(result.get().handle()).isEqualTo("hxnjxxu");
        assertThat(result.get().source()).isEqualTo(IgHandleSource.MENTION);
    }

    @Test
    @DisplayName("URL 형태는 신뢰도가 가장 높다")
    void urlHasHighestConfidence() {
        Optional<IgHandle> result = extractor.extract(
                "매일 10분 홈트\n📷 https://www.instagram.com/fitgpt_daily");

        assertThat(result).isPresent();
        assertThat(result.get().source()).isEqualTo(IgHandleSource.URL);
        assertThat(result.get().confidence()).isEqualByComparingTo("0.95");
    }

    @Test
    @DisplayName("URL 을 먼저 제거해야 .com 이 핸들로 잡히지 않는다")
    void urlIsRemovedBeforeLabelPattern() {
        // www.instagram.com 의 'instagram' 이 라벨 패턴에 걸리면 뒤의 '.com' 을 핸들로 오인한다
        Optional<IgHandle> result = extractor.extract(
                "인스타그램 https://www.instagram.com/jieun.diet");

        assertThat(result).isPresent();
        assertThat(result.get().handle()).isEqualTo("jieun.diet");
    }

    @Test
    @DisplayName("이메일 도메인이 핸들로 잡히지 않는다")
    void emailIsNotMistakenForHandle() {
        Optional<IgHandle> result = extractor.extract("비즈니스 문의: contact@fitgpt.co.kr");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("게시물 링크에서는 핸들을 뽑지 않는다")
    void postUrlIsNotAHandle() {
        Optional<IgHandle> result = extractor.extract(
                "지난 게시물: https://www.instagram.com/p/Cx1234abcd/");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("신뢰도가 높은 방식이 우선한다")
    void higherConfidenceWins() {
        Optional<IgHandle> result = extractor.extract("""
                협업 @some_manager
                https://www.instagram.com/real_account
                """);

        assertThat(result).isPresent();
        assertThat(result.get().handle()).isEqualTo("real_account");
        assertThat(result.get().source()).isEqualTo(IgHandleSource.URL);
    }

    @ParameterizedTest
    @DisplayName("설명이 비었거나 인스타 정보가 없으면 빈 결과 (실측상 64%가 이 경우)")
    @ValueSource(strings = {
            "",
            " ",
            "구독과 좋아요 부탁드립니다",
            "문의: homet@naver.com",
            "매일 브이로그 올려요 :)"
    })
    void emptyWhenNoInstagramInfo(String description) {
        assertThat(extractor.extract(description)).isEmpty();
    }

    @Test
    @DisplayName("null 설명도 예외 없이 처리한다")
    void handlesNullDescription() {
        assertThat(extractor.extract(null)).isEmpty();
    }
}
