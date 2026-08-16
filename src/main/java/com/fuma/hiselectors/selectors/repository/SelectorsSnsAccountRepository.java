package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectorsSnsAccountRepository
        extends JpaRepository<SelectorsSnsAccount, Long> {

    Optional<SelectorsSnsAccount> findFirstBySelectorsIdAndDeletedFalseOrderByLastCollectedAtDescIdDesc(
            Long selectorsId);
}
