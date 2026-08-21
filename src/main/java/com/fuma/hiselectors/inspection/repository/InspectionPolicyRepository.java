package com.fuma.hiselectors.inspection.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionPolicyRepository extends JpaRepository<InspectionPolicy, Long> {

    Optional<InspectionPolicy> findByPlatformAndActiveTrue(SnsPlatform platform);

    Optional<InspectionPolicy> findByConfigHash(String configHash);

    List<InspectionPolicy> findAllByPlatformAndActiveTrue(SnsPlatform platform);

    List<InspectionPolicy> findAllByActiveTrue();
}
