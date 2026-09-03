package com.fuma.hiselectors.creator.repository;

import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.model.CreatorDiscoverySource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorDiscoverySourceRepository
        extends JpaRepository<CreatorDiscoverySource, Long> {

    interface DiscoverySourcePair {

        String getCategoryCode();

        Long getKeywordId();

        Long getCreatorId();
    }

    Optional<CreatorDiscoverySource> findByCreatorPoolIdAndKeywordId(
            Long creatorPoolId, Long keywordId);

    List<CreatorDiscoverySource> findByCreatorPoolId(Long creatorPoolId);

    /** 이 키워드로 발굴된 이력이 있는지. 키워드 삭제 가능 여부 판단에 쓴다. */
    boolean existsByKeywordId(Long keywordId);

    /** 이 카테고리의 키워드 중 하나라도 발굴 이력이 있는지. 카테고리 삭제 판단에 쓴다. */
    boolean existsByKeywordCategoryId(Long categoryId);

    @Modifying(flushAutomatically = true)
    @Query("delete from CreatorDiscoverySource source where source.keyword.id = :keywordId")
    int deleteAllByKeywordId(@Param("keywordId") Long keywordId);

    /** 활성 YouTube 계정이 어떤 카테고리·키워드에서 발견됐는지 포화도 집계용으로 조회한다. */
    @Query("""
            select c.code as categoryCode,
                   k.id as keywordId,
                   creator.id as creatorId
            from CreatorDiscoverySource source
            join source.creatorPool creator
            join source.keyword k
            join k.category c
            where creator.deleted = false
              and creator.snsCode = 'YOUTUBE'
            """)
    List<DiscoverySourcePair> findActiveYoutubeSourcePairs();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from CreatorDiscoverySource source
            where source.creatorPool.id in (
                select creator.id from CreatorPool creator
                where creator.snsCode in :snsCodes
            )
            """)
    int deleteAllByCreatorPlatforms(@Param("snsCodes") List<String> snsCodes);

    /**
     * 대표 카테고리 산출용. 카테고리별 조회수 비중 합을 큰 순으로 준다.
     *
     * <p>첫 번째 원소의 카테고리 코드가 그 계정의 대표 카테고리다.
     * 규칙을 바꾸고 싶으면 이 쿼리만 고치면 되고, 재수집은 필요 없다.
     */
    @Query("""
            select new com.fuma.hiselectors.creator.dto.CategoryShare(
                       c.code, coalesce(sum(s.viewShare), 0))
            from CreatorDiscoverySource s
            join s.keyword k
            join k.category c
            where s.creatorPool.id = :creatorPoolId
            group by c.code
            order by coalesce(sum(s.viewShare), 0) desc, c.code asc
            """)
    List<CategoryShare> findCategoryShares(Long creatorPoolId);
}
