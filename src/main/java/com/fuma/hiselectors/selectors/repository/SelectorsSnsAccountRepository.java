package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelectorsSnsAccountRepository
        extends JpaRepository<SelectorsSnsAccount, Long> {

    List<SelectorsSnsAccount> findAllBySelectorsId(Long selectorsId);

    Optional<SelectorsSnsAccount> findFirstBySelectorsIdAndDeletedFalseOrderByLastCollectedAtDescIdDesc(
            Long selectorsId);

    @Query("""
            select account
            from SelectorsSnsAccount account, Selectors selectors, Application applicationEntity
            where account.selectorsId = selectors.id
              and selectors.applicationId = applicationEntity.id
              and applicationEntity.generationId = :generationId
              and account.deleted = false
            order by account.id asc
            """)
    List<SelectorsSnsAccount> findAllForGenerationOrderByIdAsc(
            @Param("generationId") Long generationId);
}
