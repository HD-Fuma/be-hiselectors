package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient.LocalAnalysis;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.stt.ContentInsight;
import com.fuma.hiselectors.stt.SttResult;
import com.fuma.hiselectors.stt.SttService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationReportService {

    // AI 자동 분석 저장 기본값 = 관리자 검수 대기.
    private static final String DEFAULT_STATUS = ReportStatus.AI_COMPLETED.name();

    private final SttService sttService;
    private final LocalAnalyzerClient analyzer;
    private final ApplicationReportRepository applicationReportRepository;
    private final ObjectMapper objectMapper;

    public ApplicationReport analyzeAndSave(
            Long applicationId, String snsCode, String snsContentId) {

        SttResult stt = sttService.transcribe(snsCode, snsContentId);
        String transcript = (nullToEmpty(stt.stt()) + "\n" + nullToEmpty(stt.ocr())).trim();
        LocalAnalysis local = analyzer.analyze(transcript);
        ContentInsight insight = stt.insight() == null ? ContentInsight.empty() : stt.insight();

        String category = local.categoryLabel();
        // 더현대 공식 카테고리로 안 잡히면 저장하지 않는다(도메인 밖 콘텐츠 제외).
        if (category == null || category.isBlank()) {
            log.warn("카테고리 부적합으로 리포트 저장 스킵. applicationId={}, snsCode={}, snsContentId={}",
                    applicationId, snsCode, snsContentId);
            throw new BusinessException(ErrorCode.REPORT_CATEGORY_NOT_SUPPORTED);
        }

        String keywords = join(local.keywordsOrEmpty());
        String tone = join1(insight.tone());
        String strength = join(insight.strengths());
        String warning = join(mergeWarnings(insight));
        String brandHistory = join(insight.collabBrands());

        return applicationReportRepository.save(ApplicationReport.builder()
                .applicationId(applicationId)
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
