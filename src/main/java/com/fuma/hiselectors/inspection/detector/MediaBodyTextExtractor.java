package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MediaBodyTextExtractor {

    public List<TextSource> extract(List<ContentMedia> media) {
        List<TextSource> sources = new ArrayList<>();
        for (ContentMedia item : media) {
            collect(item.getId(), item.getMediaType(), item.bodyOrEmpty(), sources);
        }
        return sources;
    }

    public String directString(ContentMedia media, String key) {
        Object value = media.bodyOrEmpty().get(key);
        return value instanceof String text ? text : "";
    }

    @SuppressWarnings("unchecked")
    private void collect(Long mediaId, MediaType mediaType, Object value,
                         List<TextSource> sources) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object child = entry.getValue();
                if (child instanceof String text && !text.isBlank()) {
                    sources.add(new TextSource(mediaId, mediaType, text));
                } else {
                    collect(mediaId, mediaType, child, sources);
                }
            }
        } else if (value instanceof List<?> list) {
            for (Object child : list) {
                collect(mediaId, mediaType, child, sources);
            }
        }
    }

    public record TextSource(Long contentMediaId, MediaType mediaType, String text) {
    }
}
