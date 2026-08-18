package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;

public record InspectionContext(
        Content content,
        ContentVersion version,
        Selectors selectors,
        List<ContentMedia> media
) {
    public InspectionContext {
        media = List.copyOf(media);
    }
}
