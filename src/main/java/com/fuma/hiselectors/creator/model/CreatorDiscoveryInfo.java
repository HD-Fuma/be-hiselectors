package com.fuma.hiselectors.creator.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발굴 과정에서 알게 된 판정 근거. {@link CreatorPool} 과 1:1.
 *
 * <p>여기 값들을 컬럼으로 저장해 두는 이유: 브랜드 판정이나 최소 구독자 기준은
 * 반드시 한 번은 틀린다. 수집 시점에 걸러서 버리면 기준을 고쳐도 재수집해야 하고
 * 그게 API 쿼터를 또 쓴다. 전부 저장해 두고 조회할 때 거르면 기준만 바꿔
 * 다시 조회하면 된다.
 *
 * <p>{@code followerCount}, {@code engagementRate}, {@code lastContentAt} 은
 * {@link CreatorPool} 에 이미 있으므로 중복해서 두지 않는다.
 */
@Entity
@Table(name = "creator_discovery_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorDiscoveryInfo extends BaseTimeEntity {

    /** creator_pool 과 PK 를 공유한다. */
    @Id
    @Column(name = "creator_pool_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_pool_id",
            foreignKey = @ForeignKey(name = "FK_CreatorPool_TO_CreatorDiscoveryInfo_1"))
    private CreatorPool creatorPool;

    /**
     * 브랜드 계정 신호 점수. 2점 이상이면 브랜드로 본다.
     * 채널명에서 잡히면 2점, 설명에서만 잡히면 1점.
     */
    @Column(name = "brand_score", nullable = false)
    private int brandScore;

    /** 판정 근거가 된 신호. 예) "공식 유튜브 채널, 공식(채널명)" */
    @Column(name = "brand_hits", length = 200)
    private String brandHits;

    /** 유튜브 채널 설명에서 추출한 인스타 핸들. 못 찾았으면 null. */
    @Column(name = "ig_handle", length = 30)
    private String igHandle;

    /** 핸들 추출 신뢰도. URL 0.95 / 라벨 0.75 / 단순 멘션 0.35 */
    @Column(name = "ig_confidence", precision = 3, scale = 2)
    private BigDecimal igConfidence;

    /** 기존 RDS 의 NOT NULL 컬럼. 최초 발굴 시각이며 이후 갱신하지 않는다. */
    @Column(name = "discovered_at", nullable = false, updatable = false)
    private LocalDateTime discoveredAt;

    @Builder
    private CreatorDiscoveryInfo(CreatorPool creatorPool, Integer brandScore, String brandHits,
                                 String igHandle, BigDecimal igConfidence) {
        this.creatorPool = creatorPool;
        this.brandScore = brandScore == null ? 0 : brandScore;
        this.brandHits = brandHits;
        this.igHandle = igHandle;
        this.igConfidence = igConfidence;
        this.discoveredAt = LocalDateTime.now();
    }

    /** 같은 채널이 다시 발굴됐을 때 판정 근거를 갱신한다. */
    public void refresh(int brandScore, String brandHits,
                        String igHandle, BigDecimal igConfidence) {
        this.brandScore = brandScore;
        this.brandHits = brandHits;

        if (shouldReplaceHandle(igHandle, igConfidence)) {
            this.igHandle = igHandle;
            this.igConfidence = igConfidence;
        }
    }

    /**
     * 인스타 핸들 교체 정책.
     *
     * <ul>
     *   <li>못 찾았으면 기존 값을 유지한다. 이번 수집에서 설명이 잘렸을 수도 있다.</li>
     *   <li>기존에 없었으면 채운다.</li>
     *   <li>신뢰도가 더 높으면 교체한다. URL(0.95) 이 단순 멘션(0.35) 을 이긴다.</li>
     *   <li><b>신뢰도가 같으면 교체한다.</b> 크리에이터가 채널 설명의 계정을 바꾼 경우이므로
     *       최신 값을 따라야 한다. 같다고 유지하면 옛 핸들이 영영 남는다.</li>
     *   <li>신뢰도가 더 낮으면 유지한다. 확실한 값을 불확실한 값으로 덮지 않는다.</li>
     *   <li>새 신뢰도를 모르면(null) 유지한다. 기존 값과 우열을 가릴 수 없다.</li>
     * </ul>
     */
    private boolean shouldReplaceHandle(String igHandle, BigDecimal igConfidence) {
        if (igHandle == null) {
            return false;
        }
        if (this.igHandle == null) {
            return true;
        }
        if (igConfidence == null) {
            return false;
        }
        if (this.igConfidence == null) {
            return true;
        }
        return this.igConfidence.compareTo(igConfidence) <= 0;
    }
}
