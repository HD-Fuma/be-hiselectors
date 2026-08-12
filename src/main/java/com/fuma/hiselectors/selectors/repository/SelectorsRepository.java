package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectorsRepository extends JpaRepository<Selectors, Long> {

    Optional<Selectors> findBySelectorsCode(String selectorsCode);
}
