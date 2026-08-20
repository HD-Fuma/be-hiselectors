package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentEngagement;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentEngagementRepository extends JpaRepository<ContentEngagement, Long> {

    boolean existsByContentIdAndCreatedAt(Long contentId, LocalDateTime createdAt);

    @Query("""
            select engagement from ContentEngagement engagement
            where engagement.contentId in :contentIds
              and engagement.createdAt = (
                  select max(latest.createdAt) from ContentEngagement latest
                  where latest.contentId = engagement.contentId
              )
            """)
    List<ContentEngagement> findLatestByContentIds(
            @Param("contentIds") Collection<Long> contentIds);
}
