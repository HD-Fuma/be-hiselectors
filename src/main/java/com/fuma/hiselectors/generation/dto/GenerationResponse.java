package com.fuma.hiselectors.generation.dto;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import java.time.LocalDateTime;

public record GenerationResponse(
        Long id,
        String generationName,
        LocalDateTime startDate,
        LocalDateTime endDate,
        GenerationStatus generationStatus
) {

    public static GenerationResponse from(Generation generation) {
        return new GenerationResponse(
                generation.getId(),
                generation.getGenerationName(),
                generation.getStartDate(),
                generation.getEndDate(),
                generation.getGenerationStatus()
        );
    }
}
