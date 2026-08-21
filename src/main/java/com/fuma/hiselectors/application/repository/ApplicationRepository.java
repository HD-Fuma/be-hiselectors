package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.ContentAnalysisStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
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

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByUserIdAndGenerationId(Long userId, Long generationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :applicationId")
    Optional<Application> findByIdForUpdate(@Param("applicationId") Long applicationId);

    List<Application> findByMediaCollectionStatusInAndMediaCollectionRetryCountLessThanOrderByIdAsc(
            Collection<MediaCollectionStatus> statuses, int maxRetryCount, Pageable pageable);

    /** 미디어 수집 끝났고(:collected=DONE) 분석 대기·실패(재시도 남음)인 지원자. 인스타 전용. */
    @Query("""
            SELECT a FROM Application a
            WHERE a.mediaCollectionStatus = :collected
              AND a.analysisStatus IN :statuses
              AND a.analysisRetryCount < :maxRetryCount
              AND a.snsCode = :snsCode
            ORDER BY a.id ASC
            """)
    List<Application> findAnalysisTargets(
            @Param("collected") MediaCollectionStatus collected,
            @Param("statuses") Collection<ContentAnalysisStatus> statuses,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("snsCode") SnsPlatform snsCode,
            Pageable pageable);

    @Query(value = """
            SELECT a
            FROM Application a
            WHERE (:keyword IS NULL
                OR LOWER(a.snsAccountId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT u.id FROM User u
                    WHERE u.id = a.userId
                      AND (LOWER(u.hiId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))))
              AND (:snsCode IS NULL OR a.snsCode = :snsCode)
              AND (:status IS NULL OR a.status = :status)
              AND (:generationId IS NULL OR a.generationId = :generationId)
              AND (:minimumCriteriaOnly IS NULL
                OR (:minimumCriteriaOnly = true AND (
                  (a.followerCount IS NOT NULL AND a.followerCount <= 500)
                  OR (a.mediaCollectedAt IS NOT NULL
                    AND (SELECT COUNT(m.id) FROM ApplicationMedia m
                         WHERE m.applicationId = a.id
                           AND m.publishedAt >= a.mediaCollectedAt - 90 day
                           AND m.publishedAt <= a.mediaCollectedAt) <= 3)))
                OR (:minimumCriteriaOnly = false
                  AND (a.followerCount IS NULL OR a.followerCount > 500)
                  AND (a.mediaCollectedAt IS NULL
                    OR (SELECT COUNT(m.id) FROM ApplicationMedia m
                        WHERE m.applicationId = a.id
                          AND m.publishedAt >= a.mediaCollectedAt - 90 day
                          AND m.publishedAt <= a.mediaCollectedAt) > 3)))
            """,
            countQuery = """
            SELECT COUNT(a)
            FROM Application a
            WHERE (:keyword IS NULL
                OR LOWER(a.snsAccountId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR EXISTS (
                    SELECT u.id FROM User u
                    WHERE u.id = a.userId
                      AND (LOWER(u.hiId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))))
              AND (:snsCode IS NULL OR a.snsCode = :snsCode)
              AND (:status IS NULL OR a.status = :status)
              AND (:generationId IS NULL OR a.generationId = :generationId)
              AND (:minimumCriteriaOnly IS NULL
                OR (:minimumCriteriaOnly = true AND (
                  (a.followerCount IS NOT NULL AND a.followerCount <= 500)
                  OR (a.mediaCollectedAt IS NOT NULL
                    AND (SELECT COUNT(m.id) FROM ApplicationMedia m
                         WHERE m.applicationId = a.id
                           AND m.publishedAt >= a.mediaCollectedAt - 90 day
                           AND m.publishedAt <= a.mediaCollectedAt) <= 3)))
                OR (:minimumCriteriaOnly = false
                  AND (a.followerCount IS NULL OR a.followerCount > 500)
                  AND (a.mediaCollectedAt IS NULL
                    OR (SELECT COUNT(m.id) FROM ApplicationMedia m
                        WHERE m.applicationId = a.id
                          AND m.publishedAt >= a.mediaCollectedAt - 90 day
                          AND m.publishedAt <= a.mediaCollectedAt) > 3)))
            """)
    Page<Application> searchAdmin(
            @Param("keyword") String keyword,
            @Param("snsCode") SnsPlatform snsCode,
            @Param("status") ApplicationStatus status,
            @Param("generationId") Long generationId,
            @Param("minimumCriteriaOnly") Boolean minimumCriteriaOnly,
            Pageable pageable);
}
