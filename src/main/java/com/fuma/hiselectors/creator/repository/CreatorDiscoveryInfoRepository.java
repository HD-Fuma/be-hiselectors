package com.fuma.hiselectors.creator.repository;

import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CreatorDiscoveryInfoRepository
        extends JpaRepository<CreatorDiscoveryInfo, Long> {

    /** Instagram 핸들이 추출된 활성 계정. */
    List<CreatorDiscoveryInfo>
            findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                    String snsCode);

    @Query("""
            select c.id as creatorId, c.accountId as accountId
            from CreatorPool c
            left join CreatorDiscoveryInfo i on i.creatorPool = c
            where c.deleted = false
              and c.snsCode = :snsCode
              and (i.id is null or i.recent90DayContentCount is null)
            order by c.id
            """)
    List<RecentActivityBackfillTarget> findRecentActivityBackfillTargets(
            @Param("snsCode") String snsCode);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = """
            update creator_discovery_info
               set recent_90_day_content_count = :count
             where creator_pool_id = :creatorId
               and recent_90_day_content_count is null
               and exists (
                   select 1
                     from creator_pool c
                    where c.creator_pool_id = creator_discovery_info.creator_pool_id
                      and c.is_deleted = false
                      and c.sns_code = 'YOUTUBE'
               )
            """, nativeQuery = true)
    int fillRecent90DayContentCount(@Param("creatorId") Long creatorId,
                                    @Param("count") Integer count);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            insert into creator_discovery_info (
                creator_pool_id,
                brand_score,
                discovered_at,
                recent_90_day_content_count,
                created_at,
                updated_at
            )
            select c.creator_pool_id,
                   0,
                   current_timestamp,
                   :count,
                   current_timestamp,
                   current_timestamp
              from creator_pool c
             where c.creator_pool_id = :creatorId
               and c.is_deleted = false
               and c.sns_code = 'YOUTUBE'
            """, nativeQuery = true)
    int insertRecent90DayContentCount(@Param("creatorId") Long creatorId,
                                      @Param("count") Integer count);

    interface RecentActivityBackfillTarget {
        Long getCreatorId();

        String getAccountId();
    }
}
