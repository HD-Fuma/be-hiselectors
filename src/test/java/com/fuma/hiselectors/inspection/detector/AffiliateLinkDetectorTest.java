package com.fuma.hiselectors.inspection.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AffiliateLinkDetectorTest {

    @Test
    void detectsInvalidAffiliateLink() {
        AffiliateLinkDetector detector = new AffiliateLinkDetector(
                new MediaBodyTextExtractor(), (url, code) -> false);
        ContentMedia media = ContentMedia.create(20L, null, MediaType.TEXT,
                Map.of("text", "상품 링크 https://example.com/product?id=1"));
        InspectionContext context = new InspectionContext(
                Content.create(1L, SnsPlatform.INSTAGRAM, "https://instagram.com/p/1", "POST"),
                ContentVersion.create(10L, 1L, "hash"),
                Selectors.builder().selectorsRoleId("SELECTORS").selectorsCode("SEL-1").build(),
                List.of(media));

        var result = detector.detect(context);

        assertThat(result).singleElement()
                .satisfies(violation ->
                        assertThat(violation.evidence().locations()).hasSize(1));
    }
}
