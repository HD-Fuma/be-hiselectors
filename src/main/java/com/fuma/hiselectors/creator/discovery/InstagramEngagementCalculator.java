package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.Media;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.MediaItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/** 최근 Instagram 게시물의 좋아요·댓글을 이용해 팔로워 대비 참여율을 계산한다. */
@Component
public class InstagramEngagementCalculator {

    private static final int RESULT_SCALE = 2;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    /**
     * 참여율(%) = 최근 게시물 평균 참여 수 / 팔로워 수 * 100.
     *
     * <p>좋아요와 댓글이 모두 누락된 게시물은 계산에서 제외한다. 한 지표만
     * 누락된 경우에는 해당 값을 0으로 보고 나머지 지표는 사용한다.
     */
    public BigDecimal calculate(Long followersCount, Media media) {
        if (followersCount == null || followersCount <= 0
                || media == null || media.data() == null) {
            return zero();
        }

        List<MediaItem> validItems = media.data().stream()
                .filter(this::hasEngagementMetric)
                .toList();
        if (validItems.isEmpty()) {
            return zero();
        }

        long totalEngagement = validItems.stream()
                .mapToLong(item -> nonNegative(item.likeCount())
                        + nonNegative(item.commentsCount()))
                .sum();

        return BigDecimal.valueOf(totalEngagement)
                .multiply(PERCENT)
                .divide(BigDecimal.valueOf(followersCount), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(validItems.size()), RESULT_SCALE, RoundingMode.HALF_UP);
    }

    private boolean hasEngagementMetric(MediaItem item) {
        return item != null && (item.likeCount() != null || item.commentsCount() != null);
    }

    private long nonNegative(Long value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(RESULT_SCALE);
    }
}
