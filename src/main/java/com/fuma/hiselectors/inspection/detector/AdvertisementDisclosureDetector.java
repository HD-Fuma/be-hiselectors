package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AdvertisementDisclosureDetector implements RuleViolationDetector {

    private static final Pattern DISCLOSURE_IGNORED_CHARACTERS =
            Pattern.compile("[\\s,.，。]+");

    private final MediaBodyTextExtractor textExtractor;
    private final List<String> disclosurePhrases;

    public AdvertisementDisclosureDetector(MediaBodyTextExtractor textExtractor,
                                           ContentInspectionProperties properties) {
        this.textExtractor = textExtractor;
        this.disclosurePhrases = properties.disclosurePhrasesOrDefault();
    }

    @Override
    public List<DetectedViolation> detect(InspectionContext context) {
        if (hasDisclosure(context)) {
            return List.of();
        }
        return List.of(new DetectedViolation(
                ViolationTypeCode.AD_DISCLOSURE_INVALID,
                new ViolationEvidence(
                        "제목 또는 본문 첫 줄의 광고·수수료 안내 문구 및 영상 내부 광고 안내 문구를 확인할 수 없습니다.",
                        1.0, AbsenceEvidenceMarker.forContent(context, "제목 또는 본문 첫 줄"),
                        EvidenceSource.RULE)));
    }

    private boolean hasDisclosure(InspectionContext context) {
        boolean textStart = context.media().stream()
                .filter(media -> media.getMediaType() == MediaType.TEXT)
                .anyMatch(this::hasDisclosureAtStart);
        if (textStart) {
            return true;
        }
        return context.media().stream()
                .filter(media -> media.getMediaType() != MediaType.TEXT)
                .flatMap(media -> textExtractor.extract(List.of(media)).stream())
                .anyMatch(source -> containsDisclosure(source.text()));
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
        String normalized = normalizeDisclosure(text);
        return disclosurePhrases.stream()
                .map(this::normalizeDisclosure)
                .anyMatch(normalized::contains);
    }

    private String normalizeDisclosure(String text) {
        return text == null ? "" : DISCLOSURE_IGNORED_CHARACTERS.matcher(text).replaceAll("");
    }
}
