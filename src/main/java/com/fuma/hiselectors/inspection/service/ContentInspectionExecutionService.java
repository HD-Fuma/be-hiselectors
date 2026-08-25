package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.detector.AiViolationDetector;
import com.fuma.hiselectors.inspection.detector.RuleViolationDetector;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.service.MediaPreprocessingService.MediaExtractionUpdate;
import com.fuma.hiselectors.inspection.service.MediaPreprocessingService.PreprocessingResult;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskLeaseTransaction;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final EvidenceLocationNormalizer evidenceLocationNormalizer;
    private final ViolationReconciliationService reconciliationService;
    private final TaskLeaseTransaction taskLeaseTransaction;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public InspectionResult inspect(Long contentVersionId) {
        InspectionPreparation preparation = Objects.requireNonNull(
                transactionTemplate.execute(status -> prepare(contentVersionId)));
        try {
            InspectionAnalysis analysis = analyze(preparation);
            InspectionResult result = toResult(preparation, analysis);
            return Objects.requireNonNull(transactionTemplate.execute(
                    status -> {
                        persist(preparation, analysis);
                        return result;
                    }));
        } catch (RuntimeException inspectionFailure) {
            try {
                transactionTemplate.executeWithoutResult(
                        status -> fail(preparation.version().getId()));
            } catch (RuntimeException statusFailure) {
                inspectionFailure.addSuppressed(statusFailure);
            }
            throw inspectionFailure;
        }
    }

    public InspectionResult inspectTracked(Long contentVersionId, TaskLease lease) {
        InspectionPreparation preparation;
        try {
            preparation = Objects.requireNonNull(
                    transactionTemplate.execute(status -> prepare(contentVersionId)));
        } catch (RuntimeException preparationFailure) {
            recordTrackedFailure(lease, null, preparationFailure);
            throw preparationFailure;
        }

        try {
            InspectionAnalysis analysis = analyze(preparation);
            InspectionResult result = toResult(preparation, analysis);
            taskLeaseTransaction.execute(
                    lease, 1, 0, 0, () -> persist(preparation, analysis));
            return result;
        } catch (RuntimeException inspectionFailure) {
            if (isLeaseLost(inspectionFailure)) {
                throw inspectionFailure;
            }
            recordTrackedFailure(
                    lease, preparation.version().getId(), inspectionFailure);
            throw inspectionFailure;
        }
    }

    private void recordTrackedFailure(
            TaskLease lease, Long contentVersionId, RuntimeException inspectionFailure) {
        try {
            taskLeaseTransaction.execute(
                    lease,
                    0,
                    1,
                    0,
                    contentVersionId == null ? () -> { } : () -> fail(contentVersionId));
        } catch (RuntimeException statusFailure) {
            if (isLeaseLost(statusFailure)) {
                statusFailure.addSuppressed(inspectionFailure);
                throw statusFailure;
            }
            inspectionFailure.addSuppressed(statusFailure);
        }
    }

    private boolean isLeaseLost(RuntimeException failure) {
        return failure instanceof BusinessException businessException
                && businessException.getErrorCode() == ErrorCode.TASK_RUN_LEASE_LOST;
    }

    private InspectionPreparation prepare(Long contentVersionId) {
        ContentVersion requested = contentVersionRepository.findById(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        Content content = contentRepository.findByIdForUpdate(requested.getContentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        requested = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        if (!requested.getVersionNo().equals(content.getLastVersionNo())) {
            throw new BusinessException(
                    ErrorCode.HISTORICAL_CONTENT_VERSION_INSPECTION_NOT_ALLOWED);
        }
        InspectionPolicy policy = inspectionPolicyService.requireActive(content.getSnsCode());
        Selectors selectors = selectorsRepository.findById(content.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        List<ContentMedia> media = contentMediaRepository
                .findByContentVersionIdOrderBySequenceNoAsc(contentVersionId);
        ContentVersion version = requested;
        boolean versionCreated = false;
        if (preprocessingService.requiresNewVersion(content, media, policy)) {
            version = contentVersionRepository.save(ContentVersion.create(
                    content.getId(), content.nextVersionNo(), requested.getContentHash(),
                    ContentVersionCreationReason.EXTRACTION_CHANGE,
                    LocalDateTime.now(clock)));
            media = contentMediaRepository.saveAll(cloneMedia(media, version.getId()));
            versionCreated = true;
        }
        version.startInspection();
        return new InspectionPreparation(
                contentVersionId, content, version, selectors, media, policy, versionCreated);
    }

    private List<ContentMedia> cloneMedia(List<ContentMedia> source, Long targetVersionId) {
        return source.stream()
                .map(media -> ContentMedia.create(
                        targetVersionId,
                        media.getMediaType(),
                        media.getMediaUrl(),
                        media.getSnsMediaId(),
                        media.getSequenceNo(),
                        media.getMediaType() == MediaType.TEXT
                                ? new LinkedHashMap<>(media.bodyOrEmpty())
                                : Map.of()))
                .toList();
    }

    private InspectionAnalysis analyze(InspectionPreparation preparation) {
        PreprocessingResult preprocessing = preprocessingService.preprocess(
                preparation.content(), preparation.media(), preparation.policy());
        InspectionContext context = new InspectionContext(
                preparation.content(), preparation.version(),
                preparation.selectors(), preparation.media());
        List<DetectedViolation> rules = new ArrayList<>();
        ruleDetectors.forEach(detector -> rules.addAll(detector.detect(context)));
        AiInspectionResponse aiResponse = preprocessing.integratedAiResult()
                .orElseGet(() -> aiViolationDetector.inspect(context, preparation.policy()));
        List<DetectedViolation> merged = resultMerger.mergeRuleFirst(
                rules, aiResponse.violations());
        merged = evidenceLocationNormalizer.normalize(context, merged);
        return new InspectionAnalysis(
                aiResponse.report(), merged, preprocessing.extractionUpdate());
    }

    private void persist(
            InspectionPreparation preparation, InspectionAnalysis analysis) {
        Long contentVersionId = preparation.version().getId();
        Content content = contentRepository.findByIdForUpdate(
                        preparation.content().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        ContentVersion version = contentVersionRepository.findByIdForUpdate(contentVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND));
        analysis.extractionUpdate().ifPresent(update -> applyExtraction(contentVersionId, update));
        contentReportRepository.save(ContentReport.create(
                contentVersionId, analysis.report(), preparation.policy().getId()));
        reconciliationService.reconcile(
                content, version, analysis.violations(), preparation.policy().getId());
        version.completeInspection(LocalDateTime.now(clock));
    }

    private InspectionResult toResult(
            InspectionPreparation preparation, InspectionAnalysis analysis) {
        return new InspectionResult(
                preparation.requestedContentVersionId(),
                preparation.version().getId(),
                preparation.versionCreated(),
                preparation.version().getCreationReason(),
                analysis.violations().size());
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

    public record InspectionResult(
            Long requestedContentVersionId,
            Long inspectedContentVersionId,
            boolean versionCreated,
            ContentVersionCreationReason creationReason,
            int violationCount) {
    }

    private record InspectionPreparation(
            Long requestedContentVersionId,
            Content content,
            ContentVersion version,
            Selectors selectors,
            List<ContentMedia> media,
            InspectionPolicy policy,
            boolean versionCreated) {
    }

    private record InspectionAnalysis(
            ContentReportData report,
            List<DetectedViolation> violations,
            java.util.Optional<MediaExtractionUpdate> extractionUpdate) {
    }
}
