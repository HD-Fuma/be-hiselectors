package com.fuma.hiselectors.inspection.repository;

import com.fuma.hiselectors.inspection.model.ViolationType;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ViolationTypeRepository extends JpaRepository<ViolationType, Long> {

    Optional<ViolationType> findByCode(ViolationTypeCode code);

    List<ViolationType> findAllByCodeIn(Collection<ViolationTypeCode> codes);
}
