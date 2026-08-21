package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import java.util.List;

public record ApplicationMediaCollectionResponse(
        Long applicationId,
        SnsPlatform snsCode,
        int fetchedCount,
        int storedCount,
        List<ApplicationMediaResponse> media
) {
}
