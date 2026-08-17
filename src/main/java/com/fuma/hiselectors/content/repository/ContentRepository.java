package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, Long> {

    boolean existsBySnsCodeAndSnsContentId(SnsPlatform snsCode, String snsContentId);

    List<Content> findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
            Long selectorsId, SnsPlatform snsCode, LocalDateTime storedAt);

    Optional<Content> findBySnsCodeAndSnsContentId(
            SnsPlatform snsCode, String snsContentId);
}
