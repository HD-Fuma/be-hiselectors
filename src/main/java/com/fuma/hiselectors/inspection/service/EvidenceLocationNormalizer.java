package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 근거가 실제 TEXT body 또는 구조화 추출 segment를 가리키는지 검증한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceLocationNormalizer {

    private final ContentMediaExtractionBodyMapper bodyMapper;

    public List<DetectedViolation> normalize(
            InspectionContext context, List<DetectedViolation> violations) {
        Map<Long, ContentMedia> mediaById = new HashMap<>();
        context.media().forEach(media -> mediaById.put(media.getId(), media));
        Comparator<EvidenceLocation> comparator = locationComparator(mediaById);

        return violations.stream().map(violation -> {
            List<EvidenceLocation> valid = new ArrayList<>();
            for (EvidenceLocation location : violation.evidence().locations()) {
                String error = validationError(
                        location, mediaById, violation.evidence().source());
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
            EvidenceLocation location,
            Map<Long, ContentMedia> mediaById,
            EvidenceSource source) {
        if (location == null) {
            return "location is null";
        }
        if (location.contentMediaId() == null || location.mediaType() == null
                || location.targetKind() == null || location.coordinateSpace() == null
                || location.excerpt() == null || location.excerpt().isBlank()) {
            return "contentMediaId, mediaType, targetKind, coordinateSpace, excerpt는 필수입니다";
        }
        ContentMedia media = mediaById.get(location.contentMediaId());
        if (media == null) {
            return "존재하지 않는 contentMediaId=" + location.contentMediaId();
        }
        if (media.getMediaType() != location.mediaType()) {
            return "mediaType이 저장된 미디어와 다릅니다";
        }
        if (source == EvidenceSource.AI && location.targetKind() == EvidenceTargetKind.MEDIA) {
            return "AI 근거는 좌표 없는 MEDIA marker를 사용할 수 없습니다";
        }
        return switch (location.targetKind()) {
            case TEXT_BODY -> validateText(location, media);
            case STT_SEGMENT -> validateSegment(location, media, MediaType.VIDEO);
            case OCR_SEGMENT -> validateSegment(location, media, null);
            case VISUAL_SEGMENT -> validateSegment(location, media, null);
            case MEDIA -> validateMediaMarker(location);
        };
    }

    private String validateText(EvidenceLocation location, ContentMedia media) {
        if (media.getMediaType() != MediaType.TEXT
                || location.coordinateSpace() != EvidenceCoordinateSpace.UTF16_CODE_UNIT
                || location.segmentId() != null
                || location.startIndex() == null || location.endIndex() == null) {
            return "TEXT_BODY는 TEXT 미디어의 UTF16_CODE_UNIT 범위여야 합니다";
        }
        String text = bodyText(media);
        if (location.startIndex() < 0
                || location.endIndex() <= location.startIndex()
                || location.endIndex() > text.length()) {
            return "UTF-16 text range가 body.text 범위를 벗어났습니다";
        }
        String referenced = text.substring(location.startIndex(), location.endIndex());
        return referenced.equals(location.excerpt())
                ? null : "excerpt가 body.text 범위의 문자열과 다릅니다";
    }

    private String validateSegment(
            EvidenceLocation location, ContentMedia media, MediaType requiredMediaType) {
        if ((requiredMediaType != null && media.getMediaType() != requiredMediaType)
                || media.getMediaType() == MediaType.TEXT
                || location.coordinateSpace() != EvidenceCoordinateSpace.CONTENT_MEDIA_SEGMENT
                || location.segmentId() == null || location.segmentId().isBlank()
                || location.startIndex() != null || location.endIndex() != null) {
            return location.targetKind() + "의 segment 참조 형식이 올바르지 않습니다";
        }
        if (!bodyMapper.isCurrentExtraction(media.bodyOrEmpty())) {
            return "현재 스키마의 콘텐츠 추출 body가 아닙니다";
        }
        ContentMediaExtractionResult extraction = bodyMapper.fromBody(media.bodyOrEmpty());
        String segmentText = segmentText(extraction, location.targetKind(), location.segmentId());
        if (segmentText == null) {
            return "존재하지 않는 segmentId=" + location.segmentId();
        }
        return segmentText.contains(location.excerpt())
                ? null : "excerpt가 참조 segment의 근거 문자열에 포함되지 않습니다";
    }

    private String validateMediaMarker(EvidenceLocation location) {
        return location.coordinateSpace() == EvidenceCoordinateSpace.NONE
                && location.segmentId() == null
                && location.startIndex() == null
                && location.endIndex() == null
                ? null : "MEDIA marker에는 좌표가 없어야 합니다";
    }

    private String segmentText(
            ContentMediaExtractionResult extraction,
            EvidenceTargetKind targetKind,
            String segmentId) {
        return switch (targetKind) {
            case STT_SEGMENT -> extraction.stt().segments().stream()
                    .filter(segment -> segment.segmentId().equals(segmentId))
                    .map(ContentMediaExtractionResult.SttSegment::text)
                    .findFirst().orElse(null);
            case OCR_SEGMENT -> extraction.ocr().segments().stream()
                    .filter(segment -> segment.segmentId().equals(segmentId))
                    .map(ContentMediaExtractionResult.OcrSegment::text)
                    .findFirst().orElse(null);
            case VISUAL_SEGMENT -> extraction.visual().segments().stream()
                    .filter(segment -> segment.segmentId().equals(segmentId))
                    .map(ContentMediaExtractionResult.VisualSegment::description)
                    .findFirst().orElse(null);
            default -> null;
        };
    }

    private String bodyText(ContentMedia media) {
        Object text = media.bodyOrEmpty().get("text");
        return text instanceof String value ? value : "";
    }

    private Comparator<EvidenceLocation> locationComparator(
            Map<Long, ContentMedia> mediaById) {
        return Comparator
                .comparingInt((EvidenceLocation location) -> targetPriority(location.targetKind()))
                .thenComparingInt(location ->
                        mediaById.get(location.contentMediaId()).getSequenceNo())
                .thenComparing(EvidenceLocation::startIndex,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(EvidenceLocation::segmentId,
                        Comparator.nullsLast(String::compareTo))
                .thenComparing(EvidenceLocation::contentMediaId);
    }

    private int targetPriority(EvidenceTargetKind targetKind) {
        return switch (targetKind) {
            case TEXT_BODY -> 0;
            case STT_SEGMENT -> 1;
            case OCR_SEGMENT -> 2;
            case VISUAL_SEGMENT -> 3;
            case MEDIA -> 4;
        };
    }
}
