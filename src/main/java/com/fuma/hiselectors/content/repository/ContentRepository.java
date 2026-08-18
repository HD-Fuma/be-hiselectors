package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.dto.ContentInspectionQueryRow;
import com.fuma.hiselectors.content.model.Content;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Long> {

    boolean existsBySnsCodeAndSnsContentId(SnsPlatform snsCode, String snsContentId);

    List<Content> findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
            Long selectorsId, SnsPlatform snsCode, LocalDateTime storedAt);

    Optional<Content> findBySnsCodeAndSnsContentId(
            SnsPlatform snsCode, String snsContentId);

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
                    version.inspectedAt,
                    version.createdAt,
                    account.accountId,
                    account.profileImageUrl)
            from Content content
            join Selectors selectors on selectors.id = content.selectorsId
            join Application applicationEntity on applicationEntity.id = selectors.applicationId
            join ContentVersion version
              on version.contentId = content.id
             and version.versionNo = content.lastVersionNo
            join SelectorsSnsAccount account
              on account.selectorsId = content.selectorsId
             and account.snsCode = content.snsCode
             and account.deleted = false
            where applicationEntity.generationId = :generationId
              and content.deleted = false
            order by content.createdAt desc, content.id desc
            """,
            countQuery = """
            select count(content)
            from Content content
            join Selectors selectors on selectors.id = content.selectorsId
            join Application applicationEntity on applicationEntity.id = selectors.applicationId
            join ContentVersion version
              on version.contentId = content.id
             and version.versionNo = content.lastVersionNo
            join SelectorsSnsAccount account
              on account.selectorsId = content.selectorsId
             and account.snsCode = content.snsCode
             and account.deleted = false
            where applicationEntity.generationId = :generationId
              and content.deleted = false
            """)
    Page<ContentInspectionQueryRow> findInspectionRowsByGenerationId(
            @Param("generationId") Long generationId,
            Pageable pageable);
}
