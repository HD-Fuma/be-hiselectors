package com.fuma.hiselectors.report;

import com.fuma.hiselectors.report.LocalAnalyzerClient.LocalAnalysis;
import com.fuma.hiselectors.report.model.ApplicationReport;
import com.fuma.hiselectors.report.model.CreatorReport;
import com.fuma.hiselectors.report.model.ReportBase;
import com.fuma.hiselectors.report.repository.ApplicationReportRepository;
import com.fuma.hiselectors.report.repository.CreatorReportRepository;
import com.fuma.hiselectors.stt.ContentInsight;
import com.fuma.hiselectors.stt.SttResult;
import com.fuma.hiselectors.stt.SttService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReportService {

    // AI 자동 분석 저장 기본값 = 관리자 검수 대기.
    private static final String DEFAULT_STATUS = ReportStatus.AI_COMPLETED.name();

    private final SttService sttService;
    private final LocalAnalyzerClient analyzer;
    private final ApplicationReportRepository applicationReportRepository;
    private final CreatorReportRepository creatorReportRepository;
    private final ObjectMapper objectMapper;

    public ReportBase analyzeAndSave(
            ReportContext context, Long targetId, String snsCode, String snsContentId) {

        SttResult stt = sttService.transcribe(snsCode, snsContentId);
        String transcript = (nullToEmpty(stt.stt()) + "\n" + nullToEmpty(stt.ocr())).trim();
        LocalAnalysis local = analyzer.analyze(transcript);
        ContentInsight insight = stt.insight() == null ? ContentInsight.empty() : stt.insight();

        String category = local.categoryLabel();
        String keywords = clip(join(local.keywordsOrEmpty()), 500);
        String contentStyle = clip(insight.contentStyle(), 19);
        String tone = clip(insight.tone(), 500);
        String strength = clip(join(insight.strengths()), 500);
        String warning = clip(join(mergeWarnings(insight)), 500);
        String brandHistory = clip(join(insight.collabBrands()), 500);
        String summaryJson = toJson(stt.summary());

        if (context == ReportContext.APPLICATION) {
            return applicationReportRepository.save(ApplicationReport.builder()
                    .applicationId(targetId)
                    .summary(summaryJson).category(category).keywords(keywords)
                    .contentStyle(contentStyle).tone(tone).strength(strength)
                    .warning(warning).brandHistory(brandHistory).status(DEFAULT_STATUS)
                    .build());
        }
        return creatorReportRepository.save(CreatorReport.builder()
                .creatorId(targetId)
                .summary(summaryJson).category(category).keywords(keywords)
                .contentStyle(contentStyle).tone(tone).strength(strength)
                .warning(warning).brandHistory(brandHistory).status(DEFAULT_STATUS)
                .build());
    }

    /** 유의점 + 넓은 위험 + (욕설 확정 시 표식)을 warning 한 칸에 합친다. */
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

    private String toJson(String summary) {
        // json 컬럼이라 유효 JSON 문자열로 저장. 문자열 직렬화라 예외 없음(Jackson3 unchecked).
        return summary == null || summary.isBlank() ? null : objectMapper.writeValueAsString(summary);
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(", ", values);
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
