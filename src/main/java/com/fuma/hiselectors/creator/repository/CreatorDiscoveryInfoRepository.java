package com.fuma.hiselectors.creator.repository;

import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreatorDiscoveryInfoRepository
        extends JpaRepository<CreatorDiscoveryInfo, Long> {

    /** Instagram 핸들이 추출된 활성 계정. */
    @EntityGraph(attributePaths = "creatorPool")
    List<CreatorDiscoveryInfo>
            findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                    String snsCode);

    /** 선택 카테고리의 키워드에서 발견된 Instagram 후보만 조회한다. */
    @Query("""
            select distinct info from CreatorDiscoveryInfo info
            join fetch info.creatorPool creator
            where creator.snsCode = :snsCode
              and creator.deleted = false
              and info.igHandle is not null
              and exists (
                  select source.id from CreatorDiscoverySource source
                  where source.creatorPool = creator
                    and source.keyword.category.id = :categoryId
              )
            order by info.id asc
            """)
    List<CreatorDiscoveryInfo> findInstagramCandidatesByCategoryId(
            String snsCode, Long categoryId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from CreatorDiscoveryInfo info
            where info.creatorPool.id in (
                select creator.id from CreatorPool creator
                where creator.snsCode in :snsCodes
            )
            """)
    int deleteAllByCreatorPlatforms(@Param("snsCodes") List<String> snsCodes);
}
