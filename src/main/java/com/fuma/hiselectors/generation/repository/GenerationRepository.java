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

    List<Generation> findAllByOrderByStartDateDescIdDesc();

    Optional<Generation>
    findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatusOrderByStartDateAsc(
            LocalDateTime startBound, LocalDateTime endBound, GenerationStatus status);

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
}
