package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.dto.ContentInspectionQueryRow;
import com.fuma.hiselectors.content.dto.ContentFormatCountProjection;
import com.fuma.hiselectors.content.dto.ContentPerformanceQueryRow;
import com.fuma.hiselectors.content.model.Content;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Long> {

    long countByDeletedFalse();

    @Query("""
            select content.contentType as contentType, count(content) as count
            from Content content
            where content.deleted = false
            group by content.contentType
            order by content.contentType
            """)
    List<ContentFormatCountProjection> countAllByContentType();

    @Query("""
            select count(distinct content)
            from Content content
            join SelectorsGeneration sg on sg.selectorsId = content.selectorsId
            where sg.generationId = :generationId
              and content.deleted = false
            """)
    long countByGenerationId(@Param("generationId") Long generationId);

    boolean existsBySnsCodeAndSnsContentId(SnsPlatform snsCode, String snsContentId);

    List<Content> findAllBySnsCodeAndSnsContentIdIn(
            SnsPlatform snsCode, Collection<String> snsContentIds);

    Optional<Content> findBySnsCodeAndSnsContentId(
            SnsPlatform snsCode, String snsContentId);

    /** 현재 기수 셀렉터스의 저장된 콘텐츠 조회 */
    @Query("""
            select content
            from Content content, Selectors selectors, SelectorsGeneration sg
            where content.selectorsId = selectors.id
              and sg.selectorsId = selectors.id
              and sg.generationId = :generationId
              and selectors.deleted = false
              and selectors.selectorsRoleId = 'ACTIVE'
            order by content.id
            """)
    List<Content> findAllByGenerationId(@Param("generationId") Long generationId);

    List<Content> findAllBySelectorsIdAndDeletedFalseOrderByCreatedAtDescIdDesc(
            Long selectorsId);

    @Query(value = """
            select new com.fuma.hiselectors.content.dto.ContentInspectionQueryRow(
                    content.id,
                    content.selectorsId,
                    selectors.selectorsNickname,
                    content.snsCode,
                    content.snsContentId,
                    content.contentUrl,
                    content.contentType,
                    content.createdAt,
                    version.id,
                    version.versionNo,
                    version.status,
                    version.inspectionDecision,
                    version.inspectedAt,
                    version.createdAt,
                    account.accountId,
                    account.profileImageUrl)
            from Content content
            join Selectors selectors on selectors.id = content.selectorsId
            join SelectorsGeneration sg on sg.selectorsId = selectors.id
            join ContentVersion version
              on version.contentId = content.id
             and version.versionNo = content.lastVersionNo
            join SelectorsSnsAccount account
              on account.selectorsId = content.selectorsId
             and account.snsCode = content.snsCode
             and account.deleted = false
            where sg.generationId = :generationId
              and selectors.deleted = false
              and content.deleted = false
            order by content.createdAt desc, content.id desc
            """,
            countQuery = """
            select count(content)
            from Content content
            join Selectors selectors on selectors.id = content.selectorsId
            join SelectorsGeneration sg on sg.selectorsId = selectors.id
            join ContentVersion version
              on version.contentId = content.id
             and version.versionNo = content.lastVersionNo
            join SelectorsSnsAccount account
              on account.selectorsId = content.selectorsId
             and account.snsCode = content.snsCode
             and account.deleted = false
            where sg.generationId = :generationId
              and selectors.deleted = false
              and content.deleted = false
            """)
    Page<ContentInspectionQueryRow> findInspectionRowsByGenerationId(
            @Param("generationId") Long generationId,
            Pageable pageable);

    @Query(value = """
            select new com.fuma.hiselectors.content.dto.ContentPerformanceQueryRow(
                    content.id,
                    content.selectorsId,
                    selectors.selectorsNickname,
                    content.snsCode,
                    content.snsContentId,
                    content.contentUrl,
                    content.contentType,
                    content.createdAt,
                    version.id,
                    account.accountId,
                    account.followerCount,
                    account.profileImageUrl)
            from Content content
            join Selectors selectors on selectors.id = content.selectorsId
            join SelectorsGeneration sg on sg.selectorsId = selectors.id
            join ContentVersion version
              on version.contentId = content.id
             and version.versionNo = content.lastVersionNo
            join SelectorsSnsAccount account
              on account.selectorsId = content.selectorsId
             and account.snsCode = content.snsCode
             and account.deleted = false
            where sg.generationId = :generationId
              and selectors.deleted = false
              and content.deleted = false
            order by content.createdAt desc, content.id desc
            """,
            countQuery = """
            select count(content)
            from Content content
            join Selectors selectors on selectors.id = content.selectorsId
            join SelectorsGeneration sg on sg.selectorsId = selectors.id
            join ContentVersion version
              on version.contentId = content.id
             and version.versionNo = content.lastVersionNo
            join SelectorsSnsAccount account
              on account.selectorsId = content.selectorsId
             and account.snsCode = content.snsCode
             and account.deleted = false
            where sg.generationId = :generationId
              and selectors.deleted = false
              and content.deleted = false
            """)
    Page<ContentPerformanceQueryRow> findPerformanceRowsByGenerationId(
            @Param("generationId") Long generationId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Content c where c.id = :contentId")
    Optional<Content> findByIdForUpdate(@Param("contentId") Long contentId);
}
