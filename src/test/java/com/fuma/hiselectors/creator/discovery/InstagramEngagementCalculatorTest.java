package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.Media;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.MediaItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstagramEngagementCalculatorTest {

    private final InstagramEngagementCalculator calculator =
            new InstagramEngagementCalculator();

    @Test
    void 최근_게시물의_평균_참여수를_팔로워수로_나눠_참여율을_계산한다() {
        Media media = new Media(List.of(
                mediaItem(900L, 100L),
                mediaItem(450L, 50L)
        ));

        BigDecimal result = calculator.calculate(10_000L, media);

        assertThat(result).isEqualByComparingTo("7.50");
    }

    @Test
    void 한쪽_지표가_누락되면_0으로_계산한다() {
        Media media = new Media(List.of(
                mediaItem(100L, null),
                mediaItem(null, 20L)
        ));

        BigDecimal result = calculator.calculate(1_000L, media);

        assertThat(result).isEqualByComparingTo("6.00");
    }

    @Test
    void 좋아요와_댓글이_모두_없는_게시물은_계산에서_제외한다() {
        Media media = new Media(List.of(
                mediaItem(100L, 20L),
                mediaItem(null, null)
        ));

        BigDecimal result = calculator.calculate(1_000L, media);

        assertThat(result).isEqualByComparingTo("12.00");
    }

    @Test
    void 팔로워나_게시물이_없으면_0을_반환한다() {
        assertThat(calculator.calculate(0L, new Media(List.of(mediaItem(10L, 1L)))))
                .isEqualByComparingTo("0.00");
        assertThat(calculator.calculate(1_000L, new Media(List.of())))
                .isEqualByComparingTo("0.00");
        assertThat(calculator.calculate(1_000L, null))
                .isEqualByComparingTo("0.00");
    }

    private MediaItem mediaItem(Long likeCount, Long commentsCount) {
        return new MediaItem(
                "media-id", null, "IMAGE", null, null, likeCount, commentsCount
        );
    }
}
