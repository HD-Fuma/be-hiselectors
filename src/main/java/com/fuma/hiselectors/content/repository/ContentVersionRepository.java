package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {

    @Query("""
            select contentVersion
            from ContentVersion contentVersion, Content content
            where contentVersion.contentId = content.id
              and contentVersion.versionNo = content.lastVersionNo
              and content.id in :contentIds
            """)
    List<ContentVersion> findCurrentByContentIdIn(
            @Param("contentIds") Collection<Long> contentIds);

    List<ContentVersion> findAllByContentIdOrderByVersionNoDesc(Long contentId);

    Optional<ContentVersion> findByIdAndContentId(Long id, Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cv from ContentVersion cv where cv.id = :contentVersionId")
    Optional<ContentVersion> findByIdForUpdate(@Param("contentVersionId") Long contentVersionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select cv
            from ContentVersion cv, Content c, Selectors selectors, SelectorsGeneration sg
            where c.id = cv.contentId
              and selectors.id = c.selectorsId
              and sg.selectorsId = selectors.id
              and sg.generationId = :generationId
              and selectors.deleted = false
              and c.deleted = false
              and cv.versionNo = c.lastVersionNo
              and cv.inspectionDecision is not null
            order by cv.id
            """)
    List<ContentVersion> findConfirmedCurrentByGenerationIdForUpdate(
            @Param("generationId") Long generationId);

    @Query("""
            select cv.id
            from ContentVersion cv, Content c, Selectors selectors, SelectorsGeneration sg
            where c.id = cv.contentId
              and selectors.id = c.selectorsId
              and sg.selectorsId = selectors.id
              and sg.generationId = :generationId
              and selectors.deleted = false
              and c.deleted = false
              and c.snsCode = :platform
              and (cv.status is null or cv.status <> :excludedStatus)
              and cv.inspectionDecision is null
              and cv.versionNo = (
                    select max(innerCv.versionNo) from ContentVersion innerCv
                    where innerCv.contentId = cv.contentId)
              and (
                    not exists (
                        select 1 from ContentReport cr
                        where cr.contentVersionId = cv.id
                          and cr.inspectionPolicyId = :inspectionPolicyId)
              )
            """)
    List<Long> findStaleLatestVersionIds(
            @Param("generationId") Long generationId,
            @Param("platform") SnsPlatform platform,
            @Param("inspectionPolicyId") Long inspectionPolicyId,
            @Param("excludedStatus") ContentVersionStatus excludedStatus,
            Pageable pageable);
}
