package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelectorsGenerationRepository
        extends JpaRepository<SelectorsGeneration, Long> {

    /** 참여 기수 이력. 기수 정보를 함께 담아 한 번에 가져온다. */
    @Query("""
            select new com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse(
                       g.id, g.generationName, g.startDate, g.endDate,
                       cast(g.status as string), sg.createdAt)
            from SelectorsGeneration sg
            join Generation g on g.id = sg.generationId
            where sg.selectorsId = :selectorsId
            order by g.startDate desc, g.id desc
            """)
    List<SelectorsGenerationResponse> findGenerationsOf(
            @Param("selectorsId") Long selectorsId);

    boolean existsBySelectorsIdAndGenerationId(Long selectorsId, Long generationId);
}
