package com.fuma.hiselectors.report;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.report.LocalAnalyzerClient.LocalAnalysis;
import com.fuma.hiselectors.report.model.ApplicationReport;
import com.fuma.hiselectors.report.model.ContentReport;
import com.fuma.hiselectors.report.repository.ApplicationReportRepository;
import com.fuma.hiselectors.report.repository.ContentReportRepository;
import com.fuma.hiselectors.stt.ContentInsight;
import com.fuma.hiselectors.stt.SttResult;
import com.fuma.hiselectors.stt.SttService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    // AI 자동 분석 저장 기본값 = 관리자 검수 대기.
    private static final String DEFAULT_STATUS = ReportStatus.AI_COMPLETED.name();

    private final SttService sttService;
    private final LocalAnalyzerClient analyzer;
    private final ApplicationReportRepository applicationReportRepository;
    private final ContentReportRepository contentReportRepository;
    private final ObjectMapper objectMapper;

    public Object analyzeAndSave(
            ReportContext context, Long targetId, String snsCode, String snsContentId) {

        SttResult stt = sttService.transcribe(snsCode, snsContentId);
        String transcript = (nullToEmpty(stt.stt()) + "\n" + nullToEmpty(stt.ocr())).trim();
        LocalAnalysis local = analyzer.analyze(transcript);
        ContentInsight insight = stt.insight() == null ? ContentInsight.empty() : stt.insight();

        String category = local.categoryLabel();
        // 더현대 공식 카테고리로 안 잡히면 저장하지 않는다(도메인 밖 콘텐츠 제외).
        if (category == null || category.isBlank()) {
            log.warn("카테고리 부적합으로 리포트 저장 스킵. context={}, targetId={}, snsCode={}, snsContentId={}",
                    context, targetId, snsCode, snsContentId);
            throw new BusinessException(ErrorCode.REPORT_CATEGORY_NOT_SUPPORTED);
        }

        String keywords = join(local.keywordsOrEmpty());
        String tone = join1(insight.tone());
        String strength = join(insight.strengths());
        String warning = join(mergeWarnings(insight));
        String brandHistory = join(insight.collabBrands());
        boolean advertisement = insight.collabBrands() != null && !insight.collabBrands().isEmpty();

        if (context == ReportContext.CONTENT) {
            String blob = toJson(analysisBlob(stt.summary(), category, keywords,
                    insight.contentStyle(), tone, strength, warning, brandHistory));
            return contentReportRepository.save(ContentReport.builder()
                    .contentVersionId(targetId)
                    .advertisementYn(advertisement)
                    .summary(blob)
                    .build());
        }
        return applicationReportRepository.save(ApplicationReport.builder()
                .applicationId(targetId)
                .summary(toJson(stt.summary()))
                .category(category)
                .keywords(clip(keywords, 500))
                .contentStyle(clip(insight.contentStyle(), 19))
                .tone(clip(tone, 500))
                .strength(clip(strength, 500))
                .warning(clip(warning, 500))
                .brandHistory(clip(brandHistory, 500))
                .status(DEFAULT_STATUS)
                .build());
    }

    /** content_report.summary 에 담을 분석 전체 블롭. */
    private Map<String, Object> analysisBlob(String summary, String category, String keywords,
            String contentStyle, String tone, String strength, String warning, String brands) {
        Map<String, Object> blob = new LinkedHashMap<>();
        blob.put("summary", summary);
        blob.put("category", category);          // 더현대 공식 코드만
        blob.put("keywords", keywords);
        blob.put("contentStyle", contentStyle);
        blob.put("tone", tone);
        blob.put("strength", strength);
        blob.put("warning", warning);
        blob.put("brands", brands);
        return blob;
    }

    /** 유의점 + 넓은 위험 + (욕설 확정 시 표식)을 합친다. */
    private List<String> mergeWarnings(ContentInsight insight) {
        List<String> merged = new ArrayList<>();
        if (insight.cautions() != null) {
            merged.addAll(insight.cautions());
        }
        if (insight.risks() != null) {
            merged.addAll(insight.risks());
        }
        if (insight.hateConfirmed()) {
            merged.add("욕설/혐오");
        }
        return merged;
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    private String join1(String value) {
        return value == null ? "" : value;
    }

    private String clip(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
