package com.fuma.hiselectors.creator.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크리에이터 풀. 1행 = 1 SNS 계정.
 *
 * <p><b>기존 테이블이므로 컬럼을 추가하지 않는다.</b> 다른 팀원들이 쓰고 있고
 * {@code proposal_history}, {@code creator_report} 가 참조하는 중심 테이블이다.
 * 발굴 과정에서만 필요한 정보는 {@link CreatorDiscoveryInfo} 에 따로 둔다.
 *
 * <p>동일 인물의 유튜브 계정과 인스타 계정은 서로 다른 행으로 각각 등록된다.
 * 계정 통합·동일인 판정은 하지 않는다.
 */
@Entity
@Table(name = "creator_pool", uniqueConstraints = @UniqueConstraint(
        name = "uk_creator_pool_sns_account",
        columnNames = {"sns_code", "account_id"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorPool extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "creator_pool_id")
    private Long id;

    /** SNS 코드. {@code sns_code} 테이블 참조. 예) YOUTUBE, INSTAGRAM */
    @Column(name = "sns_code", length = 20)
    private String snsCode;

    /** 플랫폼상의 계정 식별자. 유튜브면 채널 ID, 인스타면 핸들. */
    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "creator_name", length = 100)
    private String creatorName;

    @Column(length = 100)
    private String email;

    @Column(name = "follower_count")
    private Long followerCount;

    /** 최근 활동일. 휴면 채널 판정에 쓴다. */
    @Column(name = "last_content_at")
    private LocalDateTime lastContentAt;

    /** ER 지수. (좋아요+댓글)/조회수 또는 /팔로워. */
    @Column(name = "engagement_rate", precision = 5, scale = 2)
    private BigDecimal engagementRate;

    /** 대표 카테고리 코드. {@code category.category_code} 와 같은 값. */
    @Column(length = 20)
    private String category;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder
    private CreatorPool(String snsCode, String accountId, String creatorName, String email,
                        Long followerCount, LocalDateTime lastContentAt,
                        BigDecimal engagementRate, String category) {
        this.snsCode = snsCode;
        this.accountId = accountId;
        this.creatorName = creatorName;
        this.email = email;
        this.followerCount = followerCount;
        this.lastContentAt = lastContentAt;
        this.engagementRate = engagementRate;
        this.category = category;
        this.deleted = false;
    }

    /** 발굴 배치가 지표를 다시 수집했을 때 호출한다. */
    public void updateMetrics(Long followerCount, BigDecimal engagementRate,
                              LocalDateTime lastContentAt) {
        if (followerCount != null) {
            this.followerCount = followerCount;
        }
        if (engagementRate != null) {
            this.engagementRate = engagementRate;
        }
        if (lastContentAt != null) {
            this.lastContentAt = lastContentAt;
        }
    }

    /** Instagram 사용자명 변경을 반영하면서 공개 지표도 함께 갱신한다. */
    public void updateProfile(String creatorName, Long followerCount,
                              BigDecimal engagementRate, LocalDateTime lastContentAt) {
        if (creatorName != null && !creatorName.isBlank()) {
            this.creatorName = creatorName;
        }
        updateMetrics(followerCount, engagementRate, lastContentAt);
    }

    public void updateEmail(String email) {
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
    }

    /** 사용자명을 식별자로 저장했던 기존 Instagram 행을 불변 ID 기반으로 이전한다. */
    public void migrateAccountId(String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            this.accountId = accountId;
        }
    }

    /** 발굴 출처의 조회수 비중을 집계해 산출한 대표 카테고리를 반영한다. */
    public void changeCategory(String categoryCode) {
        this.category = categoryCode;
    }

    /**
     * 소프트 삭제된 계정을 되살린다.
     *
     * <p>브랜드 계정으로 판단해 지운 채널이 다시 발굴됐을 때 쓴다.
     * 이게 없으면 지워진 계정은 재발굴돼도 영영 목록에 나오지 않는다.
     */
    public void restore() {
        this.deleted = false;
    }

    public void softDelete() {
        this.deleted = true;
    }
}
