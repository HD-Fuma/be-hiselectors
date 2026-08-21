package com.fuma.hiselectors.selectors.dto;

import com.fuma.hiselectors.selectors.model.SelectorAccessLevel;
import java.time.LocalDateTime;

public record SelectorAccessResponse(
        SelectorAccessLevel accessLevel,
        Long selectorsId,
        Long generationId,
        String generationName,
        LocalDateTime activityStartDate,
        LocalDateTime activityEndDate
) {
    public static SelectorAccessResponse of(
            SelectorAccessLevel accessLevel, Long selectorsId,
            SelectorsGenerationResponse generation) {
        return new SelectorAccessResponse(
                accessLevel,
                selectorsId,
                generation == null ? null : generation.generationId(),
                generation == null ? null : generation.generationName(),
                generation == null ? null : generation.activityStartDate(),
                generation == null ? null : generation.activityEndDate());
    }
}
