package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentVersion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {

    @Query("""
            select version
            from ContentVersion version, Content content
            where content.id in :contentIds
              and version.contentId = content.id
              and version.versionNo = content.lastVersionNo
            """)
    List<ContentVersion> findLatestByContentIdIn(
            @Param("contentIds") Collection<Long> contentIds);
}
