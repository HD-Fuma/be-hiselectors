package com.fuma.hiselectors.creator.model;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어떤 카테고리의 어떤 키워드로 발굴됐는지 기록. {@link CreatorPool} 과 1:N.
 *
 * <p>한 채널이 여러 키워드·카테고리에서 잡히는 경우가 흔하다.
 * (다이어트 브이로그 하는 사람이 화장품 리뷰도 하면 피트니스와 뷰티 양쪽에 걸린다)
 * 대표 카테고리는 {@code viewShare} 합이 가장 큰 카테고리로 정한다.
 *
 * <p>출처를 남겨두는 이유: 대표 카테고리 산출 규칙이 틀렸을 때 재수집 없이
 * 다시 계산하기 위해서다. 안 남기면 규칙을 고쳐도 쿼터를 또 써야 한다.
 */
@Entity
@Table(
        name = "creator_discovery_source",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_discovery_source", columnNames = {"creator_pool_id", "keyword_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorDiscoverySource extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_pool_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_CreatorPool_TO_CreatorDiscoverySource_1"))
    private CreatorPool creatorPool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_DiscoveryKeyword_TO_CreatorDiscoverySource_1"))
    private DiscoveryKeyword keyword;

    /**
     * 이 키워드로 걸린 영상들의 조회수가 전체에서 차지하는 비중 (0~1).
     *
     * <p>영상 개수가 아니라 조회수로 보는 이유: 뷰티 크리에이터가 홈트 영상을
     * 하나 올려 피트니스에 걸려도, 그 영상의 조회수 비중이 낮으면 뷰티로 잡힌다.
     * 채널의 실제 무게중심을 보여준다.
     */
    @Column(name = "view_share", precision = 6, scale = 5)
    private BigDecimal viewShare;

    // 최초 발굴 일시는 BaseTimeEntity 의 createdAt, 마지막 재발굴은 updatedAt 이다.

    @Builder
    private CreatorDiscoverySource(CreatorPool creatorPool, DiscoveryKeyword keyword,
                                   BigDecimal viewShare) {
        this.creatorPool = creatorPool;
        this.keyword = keyword;
        this.viewShare = viewShare;
    }

    /** 같은 키워드로 다시 발굴됐을 때 비중을 갱신한다. */
    public void refresh(BigDecimal viewShare) {
        this.viewShare = viewShare;
    }
}
