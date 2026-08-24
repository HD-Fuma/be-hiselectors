package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AdvertisementDisclosureDetector implements RuleViolationDetector {

    private final MediaBodyTextExtractor textExtractor;
    private final List<String> disclosurePhrases;

    public AdvertisementDisclosureDetector(MediaBodyTextExtractor textExtractor,
                                           ContentInspectionProperties properties) {
        this.textExtractor = textExtractor;
        this.disclosurePhrases = properties.disclosurePhrasesOrDefault();
    }

    @Override
    public List<DetectedViolation> detect(InspectionContext context) {
        boolean textDisclosure = context.media().stream()
                .filter(media -> media.getMediaType() == MediaType.TEXT)
                .anyMatch(this::hasDisclosureAtStart);
        boolean youtubeVideoDisclosure = context.media().stream()
                .filter(media -> media.getMediaType() == MediaType.VIDEO)
                .flatMap(media -> textExtractor.extract(List.of(media)).stream())
                .anyMatch(source -> containsDisclosure(source.text()));

        List<String> missing = new ArrayList<>();
        List<EvidenceLocation> locations = new ArrayList<>();
        if (!textDisclosure) {
            String label = "제목 또는 본문 첫 줄의 광고·수수료 안내 문구";
            missing.add(label);
            context.media().stream()
                    .filter(media -> media.getMediaType() == MediaType.TEXT)
                    .map(media -> marker(media, label))
                    .forEach(locations::add);
        }
        if (context.content().getSnsCode() == SnsPlatform.YOUTUBE && !youtubeVideoDisclosure) {
            String label = "영상 내부 광고 안내 문구";
            missing.add(label);
            context.media().stream()
                    .filter(media -> media.getMediaType() == MediaType.VIDEO)
                    .map(media -> marker(media, label))
                    .forEach(locations::add);
        }
        if (missing.isEmpty()) {
            return List.of();
        }

        return List.of(new DetectedViolation(
                ViolationTypeCode.AD_DISCLOSURE_INVALID,
                new ViolationEvidence(String.join(" 및 ", missing) + "를 확인할 수 없습니다.",
                        1.0, locations, EvidenceSource.RULE)));
    }

    private EvidenceLocation marker(ContentMedia media, String excerpt) {
        return new EvidenceLocation(
                media.getId(), media.getMediaType(),
                null, null, null, null, null, excerpt);
    }

    private boolean hasDisclosureAtStart(ContentMedia media) {
        String title = textExtractor.directString(media, "title");
        if (containsDisclosure(title)) {
            return true;
        }
        String text = textExtractor.directString(media, "text");
        return containsDisclosure(firstNonBlankLine(text));
    }

    private String firstNonBlankLine(String text) {
        return text.lines().map(String::trim).filter(line -> !line.isBlank())
                .findFirst().orElse("");
    }

    private boolean containsDisclosure(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        return disclosurePhrases.stream()
                .map(phrase -> phrase.replaceAll("\\s+", ""))
                .anyMatch(normalized::contains);
    }
}
