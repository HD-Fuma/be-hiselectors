package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import java.util.List;

/** 문구·링크가 없을 때 본문 시작 핀만 남긴다. 구간 인용은 하지 않는다. */
final class AbsenceEvidenceMarker {

    private AbsenceEvidenceMarker() {
    }

    static List<EvidenceLocation> forContent(InspectionContext context, String excerpt) {
        return context.media().stream()
                .filter(media -> media.getMediaType() == MediaType.TEXT)
                .findFirst()
                .or(() -> context.media().stream()
                        .filter(media -> media.getMediaType() == MediaType.VIDEO)
                        .findFirst())
                .or(() -> context.media().stream()
                        .filter(media -> media.getMediaType() != MediaType.TEXT)
                        .findFirst())
                .map(media -> marker(media, excerpt))
                .map(List::of)
                .orElseGet(List::of);
    }

    private static EvidenceLocation marker(ContentMedia media, String excerpt) {
        return new EvidenceLocation(
                media.getId(), media.getMediaType(),
                EvidenceTargetKind.MEDIA, EvidenceCoordinateSpace.NONE,
                null, null, null, excerpt);
    }
}
