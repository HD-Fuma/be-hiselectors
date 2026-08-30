package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MediaBodyTextExtractor {

    public List<TextSource> extract(List<ContentMedia> media) {
        List<TextSource> sources = new ArrayList<>();
        for (ContentMedia item : media) {
            if (item.getMediaType() == MediaType.TEXT) {
                addTextBody(item, sources);
            } else {
                addSegments(item, "stt", "text", EvidenceTargetKind.STT_SEGMENT, sources);
                addSegments(item, "ocr", "text", EvidenceTargetKind.OCR_SEGMENT, sources);
            }
        }
        return List.copyOf(sources);
    }

    public String directString(ContentMedia media, String key) {
        Object value = media.bodyOrEmpty().get(key);
        return value instanceof String text ? text : "";
    }

    private void addTextBody(ContentMedia media, List<TextSource> sources) {
        String text = directString(media, "text");
        if (!text.isBlank()) {
            sources.add(new TextSource(
                    media.getId(), media.getMediaType(), EvidenceTargetKind.TEXT_BODY,
                    null, text));
        }
    }

    private void addSegments(
            ContentMedia media,
            String groupName,
            String textField,
            EvidenceTargetKind targetKind,
            List<TextSource> sources) {
        Object groupValue = media.bodyOrEmpty().get(groupName);
        if (!(groupValue instanceof Map<?, ?> group)
                || !(group.get("segments") instanceof List<?> segments)) {
            return;
        }
        for (Object value : segments) {
            if (!(value instanceof Map<?, ?> segment)) {
                continue;
            }
            Object id = segment.get("segmentId");
            Object text = segment.get(textField);
            if (id instanceof String segmentId && !segmentId.isBlank()
                    && text instanceof String segmentText && !segmentText.isBlank()) {
                sources.add(new TextSource(
                        media.getId(), media.getMediaType(), targetKind,
                        segmentId, segmentText));
            }
        }
    }

    public record TextSource(
            Long contentMediaId,
            MediaType mediaType,
            EvidenceTargetKind targetKind,
            String segmentId,
            String text) {
    }
}
