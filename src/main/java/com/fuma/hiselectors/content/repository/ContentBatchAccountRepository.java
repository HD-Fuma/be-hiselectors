package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentBatchAccountRepository
        extends Repository<SelectorsSnsAccount, Long> {

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

    @Modifying
    @Query("""
            update SelectorsSnsAccount account
            set account.lastCollectedAt = :collectedAt,
                account.updatedAt = :collectedAt
            where account.id = :id
              and account.snsCode = :snsCode
              and account.accountId = :accountId
              and account.deleted = false
            """)
    int advanceCollectionCursorIfAccountUnchanged(
            @Param("id") Long id,
            @Param("snsCode") SnsPlatform snsCode,
            @Param("accountId") String accountId,
            @Param("collectedAt") LocalDateTime collectedAt);
}
