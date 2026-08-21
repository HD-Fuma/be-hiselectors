package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentVersion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
