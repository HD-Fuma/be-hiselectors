package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Long> {

    boolean existsBySnsCodeAndSnsContentId(SnsPlatform snsCode, String snsContentId);

    List<Content> findAllBySnsCodeAndSnsContentIdIn(
            SnsPlatform snsCode, Collection<String> snsContentIds);

    Optional<Content> findBySnsCodeAndSnsContentId(
            SnsPlatform snsCode, String snsContentId);

    /** 현재 기수 셀렉터스의 저장된 콘텐츠 조회 */
    @Query("""
            select content
            from Content content, Selectors selectors, Application application
            where content.selectorsId = selectors.id
              and selectors.applicationId = application.id
              and application.generationId = :generationId
            order by content.id
            """)
    List<Content> findAllByGenerationId(@Param("generationId") Long generationId);
}
