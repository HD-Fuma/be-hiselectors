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
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.service.MediaPreprocessingService.MediaExtractionUpdate;
import com.fuma.hiselectors.inspection.service.MediaPreprocessingService.PreprocessingResult;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ContentInspectionExecutionService {

    private final ContentVersionRepository contentVersionRepository;
    private final ContentRepository contentRepository;
    private final ContentMediaRepository contentMediaRepository;
    private final ContentReportRepository contentReportRepository;
    private final SelectorsRepository selectorsRepository;
    private final InspectionPolicyService inspectionPolicyService;
    private final MediaPreprocessingService preprocessingService;
    private final List<RuleViolationDetector> ruleDetectors;
    private final AiViolationDetector aiViolationDetector;
    private final ViolationResultMerger resultMerger;
    private final ViolationReconciliationService reconciliationService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public InspectionResult inspect(Long contentVersionId) {
        InspectionPreparation preparation = Objects.requireNonNull(
                transactionTemplate.execute(status -> prepare(contentVersionId)));
        try {
            InspectionAnalysis analysis = analyze(preparation);
            return Objects.requireNonNull(transactionTemplate.execute(
                    status -> persist(preparation, analysis)));
        } catch (RuntimeException inspectionFailure) {
            try {
                transactionTemplate.executeWithoutResult(status -> fail(contentVersionId));
            } catch (RuntimeException statusFailure) {
                inspectionFailure.addSuppressed(statusFailure);
            }
            throw inspectionFailure;
        }
    }

    private InspectionPreparation prepare(Long contentVersionId) {
        ContentVersion version = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        version.startInspection();
        Content content = contentRepository.findById(version.getContentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        InspectionPolicy policy = inspectionPolicyService.requireActive(content.getSnsCode());
        Selectors selectors = selectorsRepository.findById(content.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        List<ContentMedia> media = contentMediaRepository
                .findByContentVersionIdOrderBySequenceNoAsc(contentVersionId);
        return new InspectionPreparation(content, version, selectors, media, policy);
    }

    private InspectionAnalysis analyze(InspectionPreparation preparation) {
        PreprocessingResult preprocessing = preprocessingService.preprocess(
                preparation.content(), preparation.media(), preparation.policy());
        InspectionContext context = new InspectionContext(
                preparation.content(), preparation.version(),
                preparation.selectors(), preparation.media());
        List<DetectedViolation> rules = new ArrayList<>();
        ruleDetectors.forEach(detector -> rules.addAll(detector.detect(context)));
        AiInspectionResult aiResult = preprocessing.integratedAiResult()
                .orElseGet(() -> aiViolationDetector.inspect(context, preparation.policy()));
        List<DetectedViolation> merged = resultMerger.mergeRuleFirst(
                rules, aiResult.violations());
        return new InspectionAnalysis(aiResult, merged, preprocessing.extractionUpdate());
    }

    private InspectionResult persist(
            InspectionPreparation preparation, InspectionAnalysis analysis) {
        Long contentVersionId = preparation.version().getId();
        ContentVersion version = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        Content content = contentRepository.findById(version.getContentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        analysis.extractionUpdate().ifPresent(update -> applyExtraction(contentVersionId, update));
        contentReportRepository.save(ContentReport.create(
                contentVersionId, analysis.aiResult().report(), preparation.policy().getId()));
        reconciliationService.reconcile(
                content, version, analysis.violations(), preparation.policy().getId());
        version.completeInspection(LocalDateTime.now(clock));
        return new InspectionResult(contentVersionId, analysis.violations().size());
    }

    private void applyExtraction(Long contentVersionId, MediaExtractionUpdate update) {
        ContentMedia media = contentMediaRepository.findById(update.contentMediaId())
                .filter(candidate -> contentVersionId.equals(candidate.getContentVersionId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        media.replaceBody(update.body());
        media.markExtracted(
                update.inspectionPolicyId(), update.inputHash(), update.extractedAt());
    }

    private void fail(Long contentVersionId) {
        ContentVersion version = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        version.failInspection();
    }

    public record InspectionResult(Long contentVersionId, int violationCount) {
    }

    private record InspectionPreparation(
            Content content,
            ContentVersion version,
            Selectors selectors,
            List<ContentMedia> media,
            InspectionPolicy policy) {
    }

    private record InspectionAnalysis(
            AiInspectionResult aiResult,
            List<DetectedViolation> violations,
            java.util.Optional<MediaExtractionUpdate> extractionUpdate) {
    }
}
