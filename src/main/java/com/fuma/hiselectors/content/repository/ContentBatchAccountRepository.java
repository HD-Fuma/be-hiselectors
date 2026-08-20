package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ContentBatchAccountRepository
        extends Repository<SelectorsSnsAccount, Long> {

    @Query("""
            select account
            from SelectorsSnsAccount account, Selectors selectors, Application application
            where account.selectorsId = selectors.id
              and selectors.applicationId = application.id
              and application.generationId = :generationId
              and account.deleted = false
            order by account.id
            """)
    List<SelectorsSnsAccount> findAllByGenerationId(
            @Param("generationId") Long generationId);
}
