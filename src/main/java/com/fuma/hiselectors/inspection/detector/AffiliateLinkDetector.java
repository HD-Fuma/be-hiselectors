package com.fuma.hiselectors.inspection.detector;

import com.fuma.hiselectors.inspection.detector.MediaBodyTextExtractor.TextSource;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AffiliateLinkDetector implements RuleViolationDetector {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\\\"]+");

    private final MediaBodyTextExtractor textExtractor;
    private final AffiliateLinkValidator linkValidator;

    public AffiliateLinkDetector(MediaBodyTextExtractor textExtractor,
                                 AffiliateLinkValidator linkValidator) {
        this.textExtractor = textExtractor;
        this.linkValidator = linkValidator;
    }

    @Override
    public List<DetectedViolation> detect(InspectionContext context) {
        List<LinkLocation> links = extractLinks(textExtractor.extract(context.media()));
        if (links.stream().anyMatch(link -> linkValidator.isValid(
                link.url(), context.selectors().getSelectorsCode()))) {
            return List.of();
        }

        String reason = links.isEmpty()
                ? "콘텐츠에서 셀렉터스 제휴 링크를 확인할 수 없습니다."
                : "콘텐츠의 링크가 발급 형식 또는 셀렉터스 소유자와 일치하지 않습니다.";
        List<EvidenceLocation> locations = links.stream()
                .map(link -> new EvidenceLocation(
                        link.source().contentMediaId(), link.source().mediaType(),
                        link.source().targetKind(), coordinateSpace(link.source()),
                        link.source().segmentId(),
                        link.source().targetKind() == EvidenceTargetKind.TEXT_BODY
                                ? link.startIndex() : null,
                        link.source().targetKind() == EvidenceTargetKind.TEXT_BODY
                                ? link.endIndex() : null,
                        link.url()))
                .toList();
        return List.of(new DetectedViolation(
                ViolationTypeCode.AFFILIATE_LINK_INVALID,
                new ViolationEvidence(reason, 1.0, locations, EvidenceSource.RULE)));
    }

    private List<LinkLocation> extractLinks(List<TextSource> sources) {
        List<LinkLocation> links = new ArrayList<>();
        for (TextSource source : sources) {
            Matcher matcher = URL_PATTERN.matcher(source.text());
            while (matcher.find()) {
                String url = stripTrailingPunctuation(matcher.group());
                links.add(new LinkLocation(source, url, matcher.start(), matcher.start() + url.length()));
            }
        }
        return links;
    }

    private String stripTrailingPunctuation(String url) {
        return url.replaceFirst("[.,;:!?)\\]}]+$", "");
    }

    private EvidenceCoordinateSpace coordinateSpace(TextSource source) {
        return source.targetKind() == EvidenceTargetKind.TEXT_BODY
                ? EvidenceCoordinateSpace.UTF16_CODE_UNIT
                : EvidenceCoordinateSpace.CONTENT_MEDIA_SEGMENT;
    }

    private record LinkLocation(TextSource source, String url, int startIndex, int endIndex) {
    }
}
