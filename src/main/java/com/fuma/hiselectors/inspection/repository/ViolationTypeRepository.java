package com.fuma.hiselectors.inspection.repository;

import com.fuma.hiselectors.inspection.model.ViolationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViolationTypeRepository extends JpaRepository<ViolationType, Long> {
}
