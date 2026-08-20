package com.fuma.hiselectors.analytics.dto;

import com.fuma.hiselectors.analytics.model.ClickLog;
import com.fuma.hiselectors.analytics.model.ViewPageType;

public record ViewLogResponse(
        Long id,
        Long selectorsId,
        ViewPageType pageType,
        Long referenceId,
        Long viewerUserId
) {
    public static ViewLogResponse from(ClickLog log) {
        return new ViewLogResponse(log.getId(), log.getSelectorsId(), log.getLinkType(),
                log.getReferenceId(), log.getViewerUserId());
    }
}
