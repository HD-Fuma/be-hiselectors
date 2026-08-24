package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.ContentAnalysisStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByUserIdAndGenerationId(Long userId, Long generationId);

    /** 정량 지표 백분위 비교 모수. 미디어 수집이 끝난 지원자만 대상으로 한다. */
    List<Application> findAllByMediaCollectionStatus(MediaCollectionStatus mediaCollectionStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :applicationId")
    Optional<Application> findByIdForUpdate(@Param("applicationId") Long applicationId);

    List<Application> findByMediaCollectionStatusInAndMediaCollectionRetryCountLessThanOrderByIdAsc(
            Collection<MediaCollectionStatus> statuses, int maxRetryCount, Pageable pageable);

    /**
     * 미디어 수집 끝났고(:collected=DONE) 분석 대기·실패(재시도 남음)인 지원자.
     * :snsCodes 로 플랫폼 한정(인스타·유튜브 등). 처리 중 크래시로 :inProgress 로 멈춘
     * (analyzed_at 이 :leaseBefore 이전 = lease 만료) 것도 재대상.
     */
    @Query("""
            SELECT a FROM Application a
            WHERE a.mediaCollectionStatus = :collected
              AND a.snsCode IN :snsCodes
              AND a.analysisRetryCount < :maxRetryCount
              AND (a.analysisStatus IN :statuses
                   OR (a.analysisStatus = :inProgress AND a.analyzedAt < :leaseBefore))
            ORDER BY a.id ASC
            """)
    List<Application> findAnalysisTargets(
            @Param("collected") MediaCollectionStatus collected,
            @Param("statuses") Collection<ContentAnalysisStatus> statuses,
            @Param("inProgress") ContentAnalysisStatus inProgress,
            @Param("leaseBefore") LocalDateTime leaseBefore,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("snsCodes") Collection<SnsPlatform> snsCodes,
            Pageable pageable);

    /**
     * 분석 대상을 원자적으로 선점(IN_PROGRESS)하고 lease 시작시각(analyzed_at)을 찍는다.
     * PENDING/FAILED 이거나, 이미 IN_PROGRESS 라도 lease 가 만료(analyzed_at < :leaseBefore)된
     * 경우에만 갱신 → 여러 인스턴스가 동시에 시도해도 딱 1건만 affected=1 을 받아 중복 처리를 막고,
     * 처리 중 크래시한 건은 lease 만료 후 다른 워커가 회수한다.
     * @return 갱신된 행 수(1=선점 성공, 0=이미 다른 워커가 처리 중).
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Application a SET a.analysisStatus = :inProgress, a.analyzedAt = :now
            WHERE a.id = :id
              AND (a.analysisStatus IN :claimable
                   OR (a.analysisStatus = :inProgress AND a.analyzedAt < :leaseBefore))
            """)
    int claimForAnalysis(@Param("id") Long id,
                         @Param("inProgress") ContentAnalysisStatus inProgress,
                         @Param("claimable") Collection<ContentAnalysisStatus> claimable,
                         @Param("now") LocalDateTime now,
                         @Param("leaseBefore") LocalDateTime leaseBefore);

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
              AND EXISTS (
                  SELECT applicant.id FROM User applicant
                  WHERE applicant.id = a.userId
                    AND applicant.hiId IS NOT NULL
                    AND TRIM(applicant.hiId) <> '')
              AND (:snsCode IS NULL OR a.snsCode = :snsCode)
              AND (:status IS NULL OR a.status = :status)
              AND (:generationId IS NULL OR a.generationId = :generationId)
              AND (:minimumCriteriaOnly IS NULL
                OR (:minimumCriteriaOnly = true AND (
                  (a.followerCount IS NOT NULL AND a.followerCount <= 500)
                  OR (a.mediaCollectedAt IS NOT NULL
                    AND (SELECT COUNT(DISTINCT m.snsContentId) FROM ApplicationMedia m
                         WHERE m.applicationId = a.id
                           AND m.publishedAt >= a.mediaCollectedAt - 90 day
                           AND m.publishedAt <= a.mediaCollectedAt) <= 3)))
                OR (:minimumCriteriaOnly = false
                  AND (a.followerCount IS NULL OR a.followerCount > 500)
                  AND (a.mediaCollectedAt IS NULL
                    OR (SELECT COUNT(DISTINCT m.snsContentId) FROM ApplicationMedia m
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
              AND EXISTS (
                  SELECT applicant.id FROM User applicant
                  WHERE applicant.id = a.userId
                    AND applicant.hiId IS NOT NULL
                    AND TRIM(applicant.hiId) <> '')
              AND (:snsCode IS NULL OR a.snsCode = :snsCode)
              AND (:status IS NULL OR a.status = :status)
              AND (:generationId IS NULL OR a.generationId = :generationId)
              AND (:minimumCriteriaOnly IS NULL
                OR (:minimumCriteriaOnly = true AND (
                  (a.followerCount IS NOT NULL AND a.followerCount <= 500)
                  OR (a.mediaCollectedAt IS NOT NULL
                    AND (SELECT COUNT(DISTINCT m.snsContentId) FROM ApplicationMedia m
                         WHERE m.applicationId = a.id
                           AND m.publishedAt >= a.mediaCollectedAt - 90 day
                           AND m.publishedAt <= a.mediaCollectedAt) <= 3)))
                OR (:minimumCriteriaOnly = false
                  AND (a.followerCount IS NULL OR a.followerCount > 500)
                  AND (a.mediaCollectedAt IS NULL
                    OR (SELECT COUNT(DISTINCT m.snsContentId) FROM ApplicationMedia m
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
