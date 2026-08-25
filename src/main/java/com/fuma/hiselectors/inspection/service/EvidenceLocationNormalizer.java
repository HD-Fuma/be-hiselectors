package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.model.BoundingBox;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** EvidenceLocation 계약 검증과 영속화 순서 정규화를 담당한다. */
@Slf4j
@Component
public class EvidenceLocationNormalizer {

    public List<DetectedViolation> normalize(
            InspectionContext context, List<DetectedViolation> violations) {
        Map<Long, ContentMedia> mediaById = new HashMap<>();
        context.media().forEach(media -> mediaById.put(media.getId(), media));
        Comparator<EvidenceLocation> comparator = locationComparator(mediaById);

        return violations.stream().map(violation -> {
            List<EvidenceLocation> valid = new ArrayList<>();
            for (EvidenceLocation location : violation.evidence().locations()) {
                String error = validationError(location, mediaById);
                if (error == null) {
                    valid.add(location);
                } else if (violation.evidence().source() == EvidenceSource.AI) {
                    log.warn("잘못된 AI evidence location을 제거합니다. type={}, reason={}",
                            violation.type(), error);
                } else {
                    throw new IllegalArgumentException(
                            "RULE evidence location이 올바르지 않습니다: " + error);
                }
            }
            valid.sort(comparator);
            ViolationEvidence evidence = violation.evidence();
            return new DetectedViolation(violation.type(), new ViolationEvidence(
                    evidence.reason(), evidence.confidence(), valid, evidence.source()));
        }).toList();
    }

    private String validationError(
            EvidenceLocation location, Map<Long, ContentMedia> mediaById) {
        if (location == null) {
            return "location is null";
        }
        if (location.contentMediaId() == null || location.mediaType() == null
                || location.excerpt() == null || location.excerpt().isBlank()) {
            return "contentMediaId, mediaType, excerpt는 필수입니다";
        }
        ContentMedia media = mediaById.get(location.contentMediaId());
        if (media == null) {
            return "존재하지 않는 contentMediaId=" + location.contentMediaId();
        }
        if (media.getMediaType() != location.mediaType()) {
            return "mediaType이 저장된 미디어와 다릅니다";
        }
        if (paired(location.startIndex(), location.endIndex())
                || paired(location.startTime(), location.endTime())) {
            return "좌표의 시작과 끝은 함께 존재해야 합니다";
        }
        if (location.startIndex() != null) {
            String text = bodyText(media);
            if (location.startIndex() < 0
                    || location.endIndex() <= location.startIndex()
                    || location.endIndex() > text.length()) {
                return "UTF-16 text range가 body.text 범위를 벗어났습니다";
            }
        }
        if (location.startTime() != null
                && (location.mediaType() != MediaType.VIDEO
                || location.startTime() < 0
                || location.endTime() <= location.startTime())) {
            return "video time range가 올바르지 않습니다";
        }
        if (location.bbox() != null
                && (location.mediaType() != MediaType.IMAGE || !valid(location.bbox()))) {
            return "image bbox가 올바르지 않습니다";
        }
        return null;
    }

    private boolean paired(Object start, Object end) {
        return (start == null) != (end == null);
    }

    private boolean valid(BoundingBox bbox) {
        return bbox.x() != null && bbox.y() != null
                && bbox.width() != null && bbox.height() != null
                && bbox.x() >= 0 && bbox.y() >= 0
                && bbox.width() > 0 && bbox.height() > 0;
    }

    private String bodyText(ContentMedia media) {
        Object text = media.bodyOrEmpty().get("text");
        return text instanceof String value ? value : "";
    }

    private Comparator<EvidenceLocation> locationComparator(
            Map<Long, ContentMedia> mediaById) {
        return Comparator
                .comparingInt((EvidenceLocation location) -> mediaPriority(location.mediaType()))
                .thenComparingInt(location -> mediaById.get(location.contentMediaId()).getSequenceNo())
                .thenComparing(location -> location.startIndex(),
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(location -> location.startTime(),
                        Comparator.nullsLast(Double::compareTo))
                .thenComparing(EvidenceLocation::contentMediaId);
    }

    private int mediaPriority(MediaType mediaType) {
        return switch (mediaType) {
            case TEXT -> 0;
            case VIDEO -> 1;
            case IMAGE -> 2;
        };
    }
}
