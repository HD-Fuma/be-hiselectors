package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelectorsGenerationRepository
        extends JpaRepository<SelectorsGeneration, Long> {

    /** 참여 기수 이력. 기수 정보를 함께 담아 한 번에 가져온다. */
    @Query("""
            select new com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse(
                       g.id, g.generationName, g.startDate, g.endDate,
                       g.activityStartDate, g.activityEndDate,
                       cast(g.status as string), sg.createdAt,
                       sg.totalSales, sg.confirmedPurchaseCount, sg.paidCommissionAmount)
            from SelectorsGeneration sg
            join Generation g on g.id = sg.generationId
            where sg.selectorsId = :selectorsId
            order by g.activityStartDate desc, g.id desc
            """)
    List<SelectorsGenerationResponse> findGenerationsOf(
            @Param("selectorsId") Long selectorsId);

    boolean existsBySelectorsIdAndGenerationId(Long selectorsId, Long generationId);

    List<SelectorsGeneration> findAllByGenerationId(Long generationId);

    /** 지급 완료된 활동월에 해당하는 기수 집계 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sg
            from SelectorsGeneration sg
            join Generation g on g.id = sg.generationId
            where sg.selectorsId = :selectorsId
              and g.activityStartDate < :monthEndExclusive
              and g.activityEndDate >= :monthStart
            """)
    List<SelectorsGeneration> findAllBySelectorsIdAndActivityMonthForUpdate(
            @Param("selectorsId") Long selectorsId,
            @Param("monthStart") LocalDateTime monthStart,
            @Param("monthEndExclusive") LocalDateTime monthEndExclusive);

    @Query("""
            select g from SelectorsGeneration sg
            join Generation g on g.id = sg.generationId
            where sg.selectorsId = :selectorsId
            order by g.activityStartDate desc, g.id desc
            """)
    List<Generation> findGenerationEntitiesOf(@Param("selectorsId") Long selectorsId);
}
