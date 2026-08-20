package com.fuma.hiselectors.penalty.repository;

import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PenaltyHistoryRepository extends JpaRepository<PenaltyHistory, Long> {

    @Query("""
            select p from PenaltyHistory p
            where p.selectorsId in :selectorsIds
            order by p.selectorsId asc, p.startedAt desc, p.id desc
            """)
    List<PenaltyHistory> findAllBySelectorsIds(
            @Param("selectorsIds") Collection<Long> selectorsIds);

    @Query("""
            select p from PenaltyHistory p
            where p.selectorsId in :selectorsIds and p.generationId = :generationId
            order by p.selectorsId asc, p.startedAt desc, p.id desc
            """)
    List<PenaltyHistory> findAllBySelectorsIdsAndGenerationId(
            @Param("selectorsIds") Collection<Long> selectorsIds,
            @Param("generationId") Long generationId);

    long countBySelectorsIdAndGenerationId(Long selectorsId, Long generationId);

    List<PenaltyHistory> findAllBySelectorsIdAndGenerationIdAndStatus(
            Long selectorsId, Long generationId, PenaltyStatus status);
}
