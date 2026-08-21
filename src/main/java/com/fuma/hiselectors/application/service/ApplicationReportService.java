package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient.LocalAnalysis;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.stt.ContentInsight;
import com.fuma.hiselectors.stt.SttResult;
import com.fuma.hiselectors.stt.SttService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationReportService {

    /** applicationId 별 콘텐츠 리포트 누적 캐시. 추후 인프라 캐시(Redis 등)로 config만 교체. */
    static final String CONTENT_REPORT_CACHE = "applicationContentReports";

    private final SttService sttService;
    private final LocalAnalyzerClient analyzer;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final ApplicationRepository applicationRepository;
    private final ApplicationReportRepository reportRepository;
    private final ApplicantReportAggregator aggregator;

    /** 콘텐츠 1개를 분석해 캐시에 쌓는다. application_report 저장은 지원자 단위 취합 시점에 별도로 수행. */
    public ApplicationReport analyzeContent(
            Long applicationId, String snsCode, String snsContentId) {

        SttResult stt = sttService.transcribe(snsCode, snsContentId);
        String transcript = (nullToEmpty(stt.stt()) + "\n" + nullToEmpty(stt.ocr())).trim();
        LocalAnalysis local = analyzer.analyze(transcript);
        ContentInsight insight = stt.insight() == null ? ContentInsight.empty() : stt.insight();

        String category = local.categoryLabel();
        // 더현대 공식 카테고리로 안 잡히면 캐시에 쌓지 않는다(도메인 밖 콘텐츠 제외).
        if (category == null || category.isBlank()) {
            log.warn("카테고리 부적합으로 콘텐츠 분석 스킵. applicationId={}, snsCode={}, snsContentId={}",
                    applicationId, snsCode, snsContentId);
            throw new BusinessException(ErrorCode.REPORT_CATEGORY_NOT_SUPPORTED);
        }

        // 미저장 ApplicationReport. 점수·상태는 취합 시점에 산출·설정한다.
        ApplicationReport report = ApplicationReport.builder()
                .applicationId(applicationId)
                .summary(toJson(stt.summary()))
                .category(category)
                .keywords(clip(join(local.keywordsOrEmpty()), 500))
                .contentStyle(clip(insight.contentStyle(), 19))
                .tone(clip(join1(insight.tone()), 500))
                .strength(clip(join(insight.strengths()), 500))
                .warning(clip(join(mergeWarnings(insight)), 500))
                .brandHistory(clip(join(insight.collabBrands()), 500))
                .build();

        cacheReport(applicationId, snsCode + ":" + snsContentId, report);
        return report;
    }

    /**
     * 캐시에 쌓인 콘텐츠별 분석을 지원자 1건으로 취합해 application_report 에 저장한다.
     * 지원자당 1건(멱등 upsert). 재실행 시 기존 리포트를 지우고 다시 저장한다.
     */
    @Transactional
    public ApplicationReport aggregateAndSave(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));

        List<ApplicationReport> contents = getCachedReports(applicationId);
        if (contents.isEmpty()) {
            throw new BusinessException(ErrorCode.REPORT_CONTENT_EMPTY);
        }

        ApplicationReport aggregated = aggregator.aggregate(application, contents);
        reportRepository.deleteByApplicationId(applicationId);
        ApplicationReport saved = reportRepository.save(aggregated);
        log.info("지원자 리포트 취합·저장 완료. applicationId={}, contents={}, quantity={}, quality={}",
                applicationId, contents.size(), saved.getQuantityScore(), saved.getQualityScore());
        return saved;
    }

    /** 저장된 지원자 단위 리포트 조회. */
    public ApplicationReport getSavedReport(Long applicationId) {
        return reportRepository.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    // 콘텐츠키로 매핑 → 같은 콘텐츠 재요청은 교체(멱등). 취합 로직이 getCachedReports 로 꺼내 쓴다.
    // ponytail: 단일 인스턴스 전제. 멀티 인스턴스 전환 시 Redis 해시 연산 등으로 원자성 확보 필요.
    private synchronized void cacheReport(Long applicationId, String contentKey, ApplicationReport report) {
        Cache cache = cacheManager.getCache(CONTENT_REPORT_CACHE);
        Map<String, ApplicationReport> byContent =
                new LinkedHashMap<>(Optional.ofNullable(cachedMap(applicationId)).orElseGet(Map::of));
        byContent.put(contentKey, report);
        cache.put(applicationId, byContent);
    }

    public List<ApplicationReport> getCachedReports(Long applicationId) {
        Map<String, ApplicationReport> byContent = cachedMap(applicationId);
        return byContent == null ? List.of() : List.copyOf(byContent.values());
    }

    @SuppressWarnings("unchecked")
    private Map<String, ApplicationReport> cachedMap(Long applicationId) {
        Cache cache = cacheManager.getCache(CONTENT_REPORT_CACHE);
        return cache == null ? null : cache.get(applicationId, Map.class);
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
