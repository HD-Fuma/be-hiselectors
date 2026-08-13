package com.fuma.hiselectors.generation.repository;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationRepository extends JpaRepository<Generation, Long> {


    Optional<Generation>
    findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualAndGenerationStatusOrderByStartDateAsc(
            LocalDateTime startBound, LocalDateTime endBound, GenerationStatus generationStatus);
}
