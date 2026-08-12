package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.Selector;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectorRepository extends JpaRepository<Selector, Long> {

    Optional<Selector> findBySelectorsCode(String selectorsCode);
}
