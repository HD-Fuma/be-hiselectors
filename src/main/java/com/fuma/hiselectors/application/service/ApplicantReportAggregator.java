package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationReport;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 콘텐츠별 {@link ApplicationReport} 여러 건을 지원자 1건으로 취합한다.
 * 카테고리·스타일 = 최빈값, 키워드·톤·강점·주의·브랜드 = 중복제거 병합, 요약 = JSON 배열.
 *
 * <p>정량·정성 점수(quantityScore/qualityScore)는 산출 로직 확정 전까지 null 로 둔다
 * (엔티티 주석과 동일). 병합 전략·점수 산식은 비즈니스 확정 후 조정 대상.
 */
@Component
public class ApplicantReportAggregator {

    private static final int TEXT_MAX = 500;
    private static final int STYLE_MAX = 19;

    public ApplicationReport aggregate(Application application, List<ApplicationReport> contents) {
        return ApplicationReport.builder()
                .applicationId(application.getId())
                .category(mode(contents, ApplicationReport::getCategory))
                .keywords(clip(union(contents, ApplicationReport::getKeywords), TEXT_MAX))
                .contentStyle(clip(mode(contents, ApplicationReport::getContentStyle), STYLE_MAX))
                .tone(clip(union(contents, ApplicationReport::getTone), TEXT_MAX))
                .strength(clip(union(contents, ApplicationReport::getStrength), TEXT_MAX))
                .warning(clip(union(contents, ApplicationReport::getWarning), TEXT_MAX))
                .brandHistory(clip(union(contents, ApplicationReport::getBrandHistory), TEXT_MAX))
                .summary(summaryArray(contents))
                .status(ReportStatus.AI_COMPLETED.name())
                .build();
    }

    /** 최빈 non-blank 값. 없으면 null. */
    private String mode(List<ApplicationReport> contents, Function<ApplicationReport, String> getter) {
        return contents.stream()
                .map(getter)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** ", " 로 이어진 값들을 펼쳐 순서보존 중복제거 후 다시 잇는다. 없으면 null. */
    private String union(List<ApplicationReport> contents, Function<ApplicationReport, String> getter) {
        Set<String> merged = new LinkedHashSet<>();
        for (ApplicationReport c : contents) {
            String value = getter.apply(c);
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    merged.add(trimmed);
                }
            }
        }
        return merged.isEmpty() ? null : String.join(", ", merged);
    }

    /** 콘텐츠별 요약(각자 유효 JSON)들을 JSON 배열로 감싼다. 없으면 null. */
    private String summaryArray(List<ApplicationReport> contents) {
        List<String> parts = contents.stream()
                .map(ApplicationReport::getSummary)
                .filter(s -> s != null && !s.isBlank())
                .toList();
        return parts.isEmpty() ? null : "[" + String.join(",", parts) + "]";
    }

    private String clip(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
