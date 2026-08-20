package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentEngagement;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentEngagementRepository extends JpaRepository<ContentEngagement, Long> {

    boolean existsByContentIdAndCreatedAt(Long contentId, LocalDateTime createdAt);
}
