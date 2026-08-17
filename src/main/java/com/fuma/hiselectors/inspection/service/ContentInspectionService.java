package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.detector.AiViolationDetector;
import com.fuma.hiselectors.inspection.detector.RuleViolationDetector;
import com.fuma.hiselectors.inspection.model.AiInspectionResult;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentInspectionService {

    private final ContentVersionRepository contentVersionRepository;
    private final ContentRepository contentRepository;
    private final ContentMediaRepository contentMediaRepository;
    private final ContentReportRepository contentReportRepository;
    private final SelectorsRepository selectorsRepository;
    private final MediaPreprocessingService preprocessingService;
    private final List<RuleViolationDetector> ruleDetectors;
    private final AiViolationDetector aiViolationDetector;
    private final ViolationResultMerger resultMerger;
    private final ViolationReconciliationService reconciliationService;
    private final Clock clock;

    @Transactional
    public InspectionResult inspect(Long contentVersionId) {
        ContentVersion version = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        version.startInspection();
        Content content = contentRepository.findById(version.getContentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        Selectors selectors = selectorsRepository.findById(content.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        List<ContentMedia> media = contentMediaRepository
                .findAllByContentVersionIdOrderById(contentVersionId);

        preprocessingService.preprocess(media);
        InspectionContext context = new InspectionContext(content, version, selectors, media);
        List<DetectedViolation> rules = new ArrayList<>();
        ruleDetectors.forEach(detector -> rules.addAll(detector.detect(context)));
        AiInspectionResult aiResult = aiViolationDetector.inspect(context);
        List<DetectedViolation> merged = resultMerger.mergeRuleFirst(
                rules, aiResult.violations());

        contentReportRepository.findByContentVersionId(contentVersionId)
                .ifPresentOrElse(
                        report -> report.update(aiResult.report()),
                        () -> contentReportRepository.save(
                                ContentReport.create(contentVersionId, aiResult.report())));
        reconciliationService.reconcile(content, version, merged);
        version.completeInspection(LocalDateTime.now(clock));
        return new InspectionResult(contentVersionId, merged.size());
    }

    public record InspectionResult(Long contentVersionId, int violationCount) {
    }
}
