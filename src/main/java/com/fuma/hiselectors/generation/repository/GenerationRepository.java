package com.fuma.hiselectors.generation.repository;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationRepository extends JpaRepository<Generation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Generation g order by g.id")
    List<Generation> findAllForUpdate();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select g from Generation g order by g.id")
    List<Generation> findAllForRead();

    List<Generation> findAllByOrderByStartDateDescIdDesc();

    Optional<Generation>
    findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
            LocalDateTime startBound, LocalDateTime endBound, GenerationStatus status);

    Optional<Generation>
    findFirstByActivityStartDateLessThanEqualAndActivityEndDateGreaterThanEqualOrderByActivityStartDateAsc(
            LocalDateTime startBound, LocalDateTime endBound);

    @Query("""
            select count(g) > 0
            from Generation g
            where g.status = :status
              and g.startDate <= :endDate
              and g.endDate >= :startDate
              and (:excludedId is null or g.id <> :excludedId)
            """)
    boolean existsOverlapping(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") GenerationStatus status,
            @Param("excludedId") Long excludedId);

    @Query("""
            select count(g) > 0
            from Generation g
            where g.activityStartDate <= :activityEndDate
              and g.activityEndDate >= :activityStartDate
              and (:excludedId is null or g.id <> :excludedId)
            """)
    boolean existsActivityOverlapping(
            @Param("activityStartDate") LocalDateTime activityStartDate,
            @Param("activityEndDate") LocalDateTime activityEndDate,
            @Param("excludedId") Long excludedId);

    List<Generation> findAllByActivityEndDateLessThanOrderByActivityEndDateAsc(
            LocalDateTime endedBefore);

    Optional<Generation>
    findFirstByActivityStartDateGreaterThanOrderByActivityStartDateAscIdAsc(
            LocalDateTime currentActivityEndDate);

    @Query("""
            select g.id
            from Generation g
            where g.selectorExcellenceSelectedAt is null
              and g.activityEndDate < :endedBefore
            order by g.activityEndDate asc, g.id asc
            """)
    List<Long> findExcellenceSelectionCandidateIds(
            @Param("endedBefore") LocalDateTime endedBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Generation g where g.id = :generationId")
    Optional<Generation> findByIdForUpdate(@Param("generationId") Long generationId);
}
