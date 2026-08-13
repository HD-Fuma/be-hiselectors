package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectorsSnsAccountRepository
        extends JpaRepository<SelectorsSnsAccount, Long> {

    List<SelectorsSnsAccount> findAllBySelectorsId(Long selectorsId);

    Optional<SelectorsSnsAccount> findBySelectorsIdAndSnsCode(
            Long selectorsId, SnsPlatform snsCode);
}
