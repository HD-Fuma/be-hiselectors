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

    // 최초 발굴 일시는 BaseTimeEntity 의 createdAt, 마지막 갱신은 updatedAt 이다.
    // 같은 의미의 컬럼을 따로 두면 두 값이 어긋났을 때 어느 쪽이 기준인지 모호해진다.

    @Builder
    private CreatorDiscoveryInfo(CreatorPool creatorPool, Integer brandScore, String brandHits,
                                 String igHandle, BigDecimal igConfidence) {
        this.creatorPool = creatorPool;
        this.brandScore = brandScore == null ? 0 : brandScore;
        this.brandHits = brandHits;
        this.igHandle = igHandle;
        this.igConfidence = igConfidence;
    }

    /** 같은 채널이 다시 발굴됐을 때 판정 근거를 갱신한다. */
    public void refresh(int brandScore, String brandHits,
                        String igHandle, BigDecimal igConfidence) {
        this.brandScore = brandScore;
        this.brandHits = brandHits;
        // 이미 신뢰도 높은 핸들이 있으면 낮은 값으로 덮어쓰지 않는다
        if (igHandle != null && igConfidence != null
                && (this.igConfidence == null || this.igConfidence.compareTo(igConfidence) < 0)) {
            this.igHandle = igHandle;
            this.igConfidence = igConfidence;
        }
    }
}
