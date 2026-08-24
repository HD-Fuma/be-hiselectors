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
