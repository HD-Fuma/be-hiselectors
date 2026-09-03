package com.fuma.hiselectors.creator.repository;

import com.fuma.hiselectors.creator.dto.CreatorSummary;
import com.fuma.hiselectors.creator.dto.InfluenceCandidate;
import com.fuma.hiselectors.creator.model.CreatorPool;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorPoolRepository extends JpaRepository<CreatorPool, Long> {

    /** 관리자 화면 상세 조회용. 소프트 삭제된 계정은 존재하지 않는 것으로 처리한다. */
    Optional<CreatorPool> findByIdAndDeletedFalse(Long id);

    /**
     * 발굴 파이프라인이 중복 저장을 피하려고 쓴다.
     *
     * <p>소프트 삭제된 행도 함께 찾는다. 걸러내지 않으면 이미 지운 계정이
     * 다시 발굴될 때 같은 (sns_code, account_id) 행이 하나 더 생긴다.
     * 지워진 계정을 되살릴지는 호출부가 {@code isDeleted()} 를 보고 정한다.
     *
     * <p>{@code findFirst} 인 이유: creator_pool 에는 (sns_code, account_id)
     * 유니크 제약이 없다. 기존 테이블이라 제약을 추가할 수 없으므로,
     * 어쩌다 중복 행이 생겨도 예외 대신 한 건만 돌려주게 한다.
     */
    Optional<CreatorPool> findFirstBySnsCodeAndAccountIdOrderByIdAsc(
            String snsCode, String accountId);

    /** 화면 조회용. 소프트 삭제된 계정은 제외한다. */
    Optional<CreatorPool> findFirstBySnsCodeAndAccountIdAndDeletedFalseOrderByIdAsc(
            String snsCode, String accountId);

    @Query("""
            select creator from CreatorPool creator
            join CreatorDiscoveryInfo info on info.creatorPool = creator
            where creator.deleted = true
              and creator.snsCode in :snsCodes
              and info.profileImageUrl is not null
              and trim(info.profileImageUrl) <> ''
            """)
    List<CreatorPool> findDeletedDemoCandidatesWithProfileImage(
            @Param("snsCodes") List<String> snsCodes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CreatorPool creator
            set creator.deleted = true
            where creator.deleted = false
              and creator.snsCode in :snsCodes
            """)
    int softDeleteAllActiveByPlatforms(@Param("snsCodes") List<String> snsCodes);

    /** 브랜드 계정을 제외한 카테고리·플랫폼별 영향력 계산 후보. */
    @Query("""
            select new com.fuma.hiselectors.creator.dto.InfluenceCandidate(
                       c.id, c.snsCode, c.accountId, c.creatorName,
                       c.followerCount, c.engagementRate, c.lastContentAt, c.category,
                       c.createdAt, c.updatedAt)
            from CreatorPool c
            left join CreatorDiscoveryInfo i on i.creatorPool = c
            where c.deleted = false
              and c.category = :categoryCode
              and c.snsCode = :snsCode
              and coalesce(i.brandScore, 0) <= :maxBrandScore
              and c.lastContentAt >= :activeAfter
            """)
    List<InfluenceCandidate> findInfluenceCandidates(
            @Param("categoryCode") String categoryCode,
            @Param("snsCode") String snsCode,
            @Param("maxBrandScore") Integer maxBrandScore,
            @Param("activeAfter") LocalDateTime activeAfter);

    /** 일일 리포트 후보 선정용. 한 카테고리의 플랫폼별 비교 대상을 함께 조회한다. */
    @Query("""
            select new com.fuma.hiselectors.creator.dto.InfluenceCandidate(
                       c.id, c.snsCode, c.accountId, c.creatorName,
                       c.followerCount, c.engagementRate, c.lastContentAt, c.category,
                       c.createdAt, c.updatedAt)
            from CreatorPool c
            left join CreatorDiscoveryInfo i on i.creatorPool = c
            where c.deleted = false
              and c.category = :categoryCode
              and coalesce(i.brandScore, 0) <= :maxBrandScore
              and c.lastContentAt >= :activeAfter
            """)
    List<InfluenceCandidate> findInfluenceCandidatesByCategory(
            @Param("categoryCode") String categoryCode,
            @Param("maxBrandScore") Integer maxBrandScore,
            @Param("activeAfter") LocalDateTime activeAfter);

    /**
     * 발굴된 크리에이터 목록. 조건이 null 이면 그 조건은 적용하지 않는다.
     *
     * <p>수집 시점에 거르지 않고 전부 저장해 두었으므로, 브랜드 계정이나 구독자 미달
     * 계정을 빼는 일은 전적으로 여기서 한다. 기준이 틀렸다고 판단되면 파라미터만
     * 바꿔서 다시 조회하면 되고, 재수집은 필요 없다.
     *
     * <p>발굴 정보가 없는 계정(수동 등록 등)도 나오도록 left join 을 쓴다.
     * 다만 브랜드·신뢰도 조건을 걸면 그 계정들은 자연히 빠진다.
     */
    @Query(value = """
            select new com.fuma.hiselectors.creator.dto.CreatorSummary(
                       c.id, c.snsCode, c.accountId, c.creatorName,
                       i.profileImageUrl, c.followerCount, c.engagementRate, c.lastContentAt, c.category,
                       i.recent90DayContentCount, i.discoveredAt,
                       i.brandScore, i.igHandle, i.igConfidence)
            from CreatorPool c
            left join CreatorDiscoveryInfo i on i.creatorPool = c
            where c.deleted = false
              and (:keyword is null
                   or lower(c.creatorName) like lower(concat('%', :keyword, '%'))
                   or lower(c.accountId) like lower(concat('%', :keyword, '%')))
              and (:categoryCode is null or c.category = :categoryCode)
              and (:snsCode is null or c.snsCode = :snsCode)
              and (:minFollower is null or c.followerCount >= :minFollower)
              and (:maxFollower is null or c.followerCount <= :maxFollower)
              and (:minEngagementRate is null or c.engagementRate >= :minEngagementRate)
              and (:minRecent90DayContentCount is null
                   or i.recent90DayContentCount >= :minRecent90DayContentCount)
              and (:maxBrandScore is null or coalesce(i.brandScore, 0) <= :maxBrandScore)
              and (:minIgConfidence is null or i.igConfidence >= :minIgConfidence)
              and (:activeAfter is null or c.lastContentAt >= :activeAfter)
            """,
            countQuery = """
            select count(c)
            from CreatorPool c
            left join CreatorDiscoveryInfo i on i.creatorPool = c
            where c.deleted = false
              and (:keyword is null
                   or lower(c.creatorName) like lower(concat('%', :keyword, '%'))
                   or lower(c.accountId) like lower(concat('%', :keyword, '%')))
              and (:categoryCode is null or c.category = :categoryCode)
              and (:snsCode is null or c.snsCode = :snsCode)
              and (:minFollower is null or c.followerCount >= :minFollower)
              and (:maxFollower is null or c.followerCount <= :maxFollower)
              and (:minEngagementRate is null or c.engagementRate >= :minEngagementRate)
              and (:minRecent90DayContentCount is null
                   or i.recent90DayContentCount >= :minRecent90DayContentCount)
              and (:maxBrandScore is null or coalesce(i.brandScore, 0) <= :maxBrandScore)
              and (:minIgConfidence is null or i.igConfidence >= :minIgConfidence)
              and (:activeAfter is null or c.lastContentAt >= :activeAfter)
            """)
    Page<CreatorSummary> search(@Param("keyword") String keyword,
                                @Param("categoryCode") String categoryCode,
                                @Param("snsCode") String snsCode,
                                @Param("minFollower") Long minFollower,
                                @Param("maxFollower") Long maxFollower,
                                @Param("minEngagementRate") BigDecimal minEngagementRate,
                                @Param("minRecent90DayContentCount")
                                Integer minRecent90DayContentCount,
                                @Param("maxBrandScore") Integer maxBrandScore,
                                @Param("minIgConfidence") BigDecimal minIgConfidence,
                                @Param("activeAfter") LocalDateTime activeAfter,
                                Pageable pageable);

    default Page<CreatorSummary> search(String categoryCode, String snsCode,
                                        Long minFollower, Integer maxBrandScore,
                                        BigDecimal minIgConfidence, LocalDateTime activeAfter,
                                        Pageable pageable) {
        return search(null, categoryCode, snsCode, minFollower, null, null, null,
                maxBrandScore, minIgConfidence, activeAfter, pageable);
    }
}
