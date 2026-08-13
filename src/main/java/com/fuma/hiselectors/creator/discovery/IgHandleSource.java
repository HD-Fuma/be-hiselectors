package com.fuma.hiselectors.creator.discovery;

import java.math.BigDecimal;

/**
 * 인스타 핸들을 어떤 방식으로 찾았는지. 값이 클수록 믿을 만하다.
 *
 * <p><b>실측 주의</b>: 뷰티 키워드로 실제 발굴했을 때 {@link #URL} 은 <b>0건</b>이었다.
 * 한국 크리에이터는 {@code https://instagram.com/handle} 대신
 * {@code Instagram\n@handle} 처럼 적는다. 따라서 실질적인 최고 등급은 {@link #LABELED}
 * 이며, 자동 승인 임계값을 0.95 로 잡으면 아무도 통과하지 못한다.
 */
public enum IgHandleSource {

    /** {@code https://instagram.com/handle} — 확실하지만 실제로는 드물다. */
    URL(new BigDecimal("0.95")),

    /** {@code Instagram @handle}, {@code 인스타 : handle} — 실측상 가장 흔하다. */
    LABELED(new BigDecimal("0.75")),

    /** {@code @handle} 만 있는 경우. 다른 사람을 태그한 것일 수 있다. */
    MENTION(new BigDecimal("0.35"));

    private final BigDecimal confidence;

    IgHandleSource(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public BigDecimal confidence() {
        return confidence;
    }
}
