package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentBatchAccountRepository
        extends Repository<SelectorsSnsAccount, Long> {

    SelectorsSnsAccount save(SelectorsSnsAccount account);

    @Query("""
            select account
            from SelectorsSnsAccount account, Selectors selectors, SelectorsGeneration sg
            where account.selectorsId = selectors.id
              and sg.selectorsId = selectors.id
              and sg.generationId = :generationId
              and selectors.deleted = false
              and account.deleted = false
            order by account.id
            """)
    List<SelectorsSnsAccount> findAllByGenerationId(
            @Param("generationId") Long generationId);
}
