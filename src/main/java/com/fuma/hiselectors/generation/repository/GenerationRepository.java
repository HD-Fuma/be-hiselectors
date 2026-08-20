package com.fuma.hiselectors.generation.repository;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GenerationRepository extends JpaRepository<Generation, Long> {

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
}
