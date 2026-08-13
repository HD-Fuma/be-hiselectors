package com.fuma.hiselectors.application.repository;

import com.fuma.hiselectors.application.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByUserIdAndGenerationId(Long userId, Long generationId);
}
