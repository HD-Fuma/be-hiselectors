package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.Selector;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectorRepository extends JpaRepository<Selector, Long> {
}
