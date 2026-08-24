package com.fuma.hiselectors.selectors.excellence.repository;

import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelectorExcellenceSelectionRepository
        extends JpaRepository<SelectorExcellenceSelection, Long> {

    @Query("""
            select selection
            from SelectorExcellenceSelection selection
            join Generation generation on generation.id = selection.generationId
            where selection.selectorsId in :selectorsIds
            order by generation.activityEndDate desc,
                     generation.id desc,
                     selection.selectorsId asc,
                     selection.selectionType asc,
                     selection.selectionId asc
            """)
    List<SelectorExcellenceSelection> findAllForSelectorsOrderByGenerationActivityEndDateDesc(
            @Param("selectorsIds") Collection<Long> selectorsIds);
}
