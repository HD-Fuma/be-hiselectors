package com.fuma.hiselectors.inspection.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdvertisementDisclosureDetectorTest {

    @Test
    void detectsMissingDisclosure() {
        AdvertisementDisclosureDetector detector = new AdvertisementDisclosureDetector(
                new MediaBodyTextExtractor(), new ContentInspectionProperties(null, null, null));
        Content content = Content.create(1L, SnsPlatform.INSTAGRAM,
                "https://instagram.com/p/1", "POST");
        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ContentMedia media = ContentMedia.create(20L, null, MediaType.TEXT,
                Map.of("text", "오늘 추천하는 향수입니다."));

        var result = detector.detect(new InspectionContext(
                content, version, selectors(), List.of(media)));

        assertThat(result).singleElement()
                .satisfies(violation -> assertThat(violation.evidence().confidence()).isEqualTo(1.0));
    }

    private Selectors selectors() {
        return Selectors.builder().selectorsRoleId("SELECTORS").selectorsCode("SEL-1").build();
    }
}
