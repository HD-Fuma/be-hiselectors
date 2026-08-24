package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.detector.AiViolationDetector;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService.InspectionResult;
import com.fuma.hiselectors.inspection.service.MediaPreprocessingService.PreprocessingResult;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskLeaseTransaction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class ContentInspectionExecutionServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-24T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void trackedSuccessCommitsDomainWorkWithOneSucceededDelta() {
        Fixture fixture = fixture();
        ContentVersion version = prepareSuccessfulInspection(fixture);
        TaskLease lease = lease();
        doAnswer(invocation -> {
            verifyNoInteractions(fixture.reports);
            TaskLeaseTransaction.LeaseProtectedWork work = invocation.getArgument(4);
            work.run();
            return null;
        }).when(fixture.leaseTransaction)
                .execute(eq(lease), eq(1L), eq(0L), eq(0L), any());

        InspectionResult result = fixture.service.inspectTracked(1L, lease);

        assertThat(result.requestedContentVersionId()).isEqualTo(1L);
        assertThat(result.inspectedContentVersionId()).isEqualTo(1L);
        assertThat(result.violationCount()).isZero();
        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.COMPLETED);
        verify(fixture.leaseTransaction, times(1))
                .execute(eq(lease), eq(1L), eq(0L), eq(0L), any());
        verify(fixture.reports).save(any(ContentReport.class));
        verify(fixture.transactions, times(1)).execute(any());
    }

    @Test
    void trackedAnalysisFailureCommitsFailedStatusAndOneFailedDelta() {
        Fixture fixture = fixture();
        ContentVersion version = prepareSuccessfulInspection(fixture);
        TaskLease lease = lease();
        IllegalStateException analysisFailure = new IllegalStateException("analysis failed");
        when(fixture.preprocessing.preprocess(any(), any(), any()))
                .thenThrow(analysisFailure);

        assertThatThrownBy(() -> fixture.service.inspectTracked(1L, lease))
                .isSameAs(analysisFailure);

        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.FAILED);
        verify(fixture.leaseTransaction, times(1))
                .execute(eq(lease), eq(0L), eq(1L), eq(0L), any());
        verifyNoInteractions(fixture.reports);
    }

    @Test
    void trackedPrepareFailureRecordsExactlyOneFailedDeltaWithEmptyWork() {
        Fixture fixture = fixture();
        TaskLease lease = lease();
        when(fixture.versions.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.inspectTracked(1L, lease))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONTENT_VERSION_NOT_FOUND));

        verify(fixture.leaseTransaction, times(1))
                .execute(eq(lease), eq(0L), eq(1L), eq(0L), any());
        verify(fixture.versions, times(1)).findById(1L);
        verifyNoInteractions(
                fixture.contents, fixture.reports, fixture.reconciliation);
    }

    @Test
    void trackedPersistFailureRecordsFailedStatusAndRethrowsOriginalError() {
        Fixture fixture = fixture();
        ContentVersion version = prepareSuccessfulInspection(fixture);
        TaskLease lease = lease();
        IllegalStateException persistFailure = new IllegalStateException("persist failed");
        when(fixture.reports.save(any(ContentReport.class))).thenThrow(persistFailure);

        assertThatThrownBy(() -> fixture.service.inspectTracked(1L, lease))
                .isSameAs(persistFailure);

        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.FAILED);
        verify(fixture.leaseTransaction).execute(
                eq(lease), eq(1L), eq(0L), eq(0L), any());
        verify(fixture.leaseTransaction).execute(
                eq(lease), eq(0L), eq(1L), eq(0L), any());
    }

    @Test
    void trackedSuccessLeaseLossStopsBeforeDomainWritesAndFailureRecording() {
        Fixture fixture = fixture();
        prepareSuccessfulInspection(fixture);
        TaskLease lease = lease();
        BusinessException leaseLost = new BusinessException(ErrorCode.TASK_RUN_LEASE_LOST);
        doThrow(leaseLost).when(fixture.leaseTransaction)
                .execute(eq(lease), eq(1L), eq(0L), eq(0L), any());

        assertThatThrownBy(() -> fixture.service.inspectTracked(1L, lease))
                .isSameAs(leaseLost);

        verify(fixture.leaseTransaction, times(1))
                .execute(any(), anyLong(), anyLong(), anyLong(), any());
        verifyNoInteractions(fixture.reports, fixture.reconciliation);
    }

    @Test
    void trackedFailureRecordingLeaseLossBecomesPrimaryWithOriginalSuppressed() {
        Fixture fixture = fixture();
        prepareSuccessfulInspection(fixture);
        TaskLease lease = lease();
        IllegalStateException analysisFailure = new IllegalStateException("analysis failed");
        BusinessException leaseLost = new BusinessException(ErrorCode.TASK_RUN_LEASE_LOST);
        when(fixture.preprocessing.preprocess(any(), any(), any()))
                .thenThrow(analysisFailure);
        doThrow(leaseLost).when(fixture.leaseTransaction)
                .execute(eq(lease), eq(0L), eq(1L), eq(0L), any());

        assertThatThrownBy(() -> fixture.service.inspectTracked(1L, lease))
                .isSameAs(leaseLost)
                .satisfies(error -> assertThat(error.getSuppressed())
                        .containsExactly(analysisFailure));
    }

    @Test
    void trackedNonLeaseFailureRecordingErrorIsSuppressedOnOriginal() {
        Fixture fixture = fixture();
        prepareSuccessfulInspection(fixture);
        TaskLease lease = lease();
        IllegalStateException analysisFailure = new IllegalStateException("analysis failed");
        IllegalStateException statusFailure = new IllegalStateException("status failed");
        when(fixture.preprocessing.preprocess(any(), any(), any()))
                .thenThrow(analysisFailure);
        doThrow(statusFailure).when(fixture.leaseTransaction)
                .execute(eq(lease), eq(0L), eq(1L), eq(0L), any());

        assertThatThrownBy(() -> fixture.service.inspectTracked(1L, lease))
                .isSameAs(analysisFailure)
                .satisfies(error -> assertThat(error.getSuppressed())
                        .containsExactly(statusFailure));
    }

    @Test
    void directSuccessKeepsItsTransactionsAndNeverUsesLeaseTransaction() {
        Fixture fixture = fixture();
        ContentVersion version = prepareSuccessfulInspection(fixture);

        InspectionResult result = fixture.service.inspect(1L);

        assertThat(result.inspectedContentVersionId()).isEqualTo(1L);
        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.COMPLETED);
        verify(fixture.transactions, times(2)).execute(any());
        verify(fixture.transactions, never()).executeWithoutResult(any());
        verifyNoInteractions(fixture.leaseTransaction);
    }

    @Test
    void directAnalysisFailureStoresFailedInItsFailureTransaction() {
        Fixture fixture = fixture();
        ContentVersion version = prepareSuccessfulInspection(fixture);
        IllegalStateException analysisFailure = new IllegalStateException("analysis failed");
        when(fixture.preprocessing.preprocess(any(), any(), any()))
                .thenThrow(analysisFailure);

        assertThatThrownBy(() -> fixture.service.inspect(1L))
                .isSameAs(analysisFailure);

        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.FAILED);
        verify(fixture.transactions, times(1)).execute(any());
        verify(fixture.transactions, times(1)).executeWithoutResult(any());
        verifyNoInteractions(fixture.leaseTransaction);
    }

    @Test
    void createsOneExtractionVersionAndRejectsTheOldVersionAfterward() {
        Fixture fixture = fixture();
        Content content = content(1L);
        ContentVersion requested = version(1L, 1L, ContentVersionCreationReason.INITIAL);
        ContentMedia legacyVideo = ContentMedia.create(
                1L, MediaType.VIDEO, null, "abc123", 0,
                Map.of("stt", List.of(), "ocr", List.of()));
        ReflectionTestUtils.setField(legacyVideo, "id", 11L);
        InspectionPolicy policy = mock(InspectionPolicy.class);
        when(policy.getId()).thenReturn(9L);

        when(fixture.versions.findById(1L)).thenReturn(Optional.of(requested));
        when(fixture.contents.findByIdForUpdate(10L)).thenReturn(Optional.of(content));
        when(fixture.versions.findByIdForUpdate(1L)).thenReturn(Optional.of(requested));
        when(fixture.policies.requireActive(SnsPlatform.YOUTUBE)).thenReturn(policy);
        when(fixture.selectors.findById(7L)).thenReturn(Optional.of(selectors()));
        when(fixture.media.findByContentVersionIdOrderBySequenceNoAsc(1L))
                .thenReturn(List.of(legacyVideo));
        when(fixture.preprocessing.requiresNewVersion(
                content, List.of(legacyVideo), policy)).thenReturn(true);
        when(fixture.media.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.preprocessing.preprocess(any(), any(), any())).thenReturn(
                successfulPreprocessing());
        when(fixture.merger.mergeRuleFirst(any(), any())).thenReturn(List.of());
        when(fixture.normalizer.normalize(any(), any())).thenReturn(List.of());
        when(fixture.versions.findByIdForUpdate(2L)).thenAnswer(invocation ->
                fixture.versions.findById(2L));
        when(fixture.versions.findById(2L)).thenAnswer(invocation -> Optional.ofNullable(
                fixture.savedVersion));
        when(fixture.versions.save(any(ContentVersion.class))).thenAnswer(invocation -> {
            ContentVersion saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            fixture.savedVersion = saved;
            return saved;
        });

        InspectionResult result = fixture.service.inspect(1L);

        assertThat(result.requestedContentVersionId()).isEqualTo(1L);
        assertThat(result.inspectedContentVersionId()).isEqualTo(2L);
        assertThat(result.versionCreated()).isTrue();
        assertThat(result.creationReason())
                .isEqualTo(ContentVersionCreationReason.EXTRACTION_CHANGE);
        assertThat(content.getLastVersionNo()).isEqualTo(2L);
        verify(fixture.versions).save(any(ContentVersion.class));

        assertThatThrownBy(() -> fixture.service.inspect(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ErrorCode.HISTORICAL_CONTENT_VERSION_INSPECTION_NOT_ALLOWED));
    }

    @Test
    void rejectsHistoricalVersionWithConflictError() {
        Fixture fixture = fixture();
        Content content = content(2L);
        ContentVersion requested = version(1L, 1L, ContentVersionCreationReason.INITIAL);
        when(fixture.versions.findById(1L)).thenReturn(Optional.of(requested));
        when(fixture.contents.findByIdForUpdate(10L)).thenReturn(Optional.of(content));
        when(fixture.versions.findByIdForUpdate(1L)).thenReturn(Optional.of(requested));

        assertThatThrownBy(() -> fixture.service.inspect(1L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(
                            ErrorCode.HISTORICAL_CONTENT_VERSION_INSPECTION_NOT_ALLOWED);
                    assertThat(exception.getErrorCode().getStatus().value()).isEqualTo(409);
                });
    }

    private ContentVersion prepareSuccessfulInspection(Fixture fixture) {
        Content content = content(1L);
        ContentVersion version = version(1L, 1L, ContentVersionCreationReason.INITIAL);
        InspectionPolicy policy = mock(InspectionPolicy.class);
        when(policy.getId()).thenReturn(9L);
        when(fixture.versions.findById(1L)).thenReturn(Optional.of(version));
        when(fixture.contents.findByIdForUpdate(10L)).thenReturn(Optional.of(content));
        when(fixture.versions.findByIdForUpdate(1L)).thenReturn(Optional.of(version));
        when(fixture.policies.requireActive(SnsPlatform.YOUTUBE)).thenReturn(policy);
        when(fixture.selectors.findById(7L)).thenReturn(Optional.of(selectors()));
        when(fixture.media.findByContentVersionIdOrderBySequenceNoAsc(1L))
                .thenReturn(List.of());
        when(fixture.preprocessing.preprocess(any(), any(), any()))
                .thenReturn(successfulPreprocessing());
        when(fixture.merger.mergeRuleFirst(any(), any())).thenReturn(List.of());
        when(fixture.normalizer.normalize(any(), any())).thenReturn(List.of());
        return version;
    }

    private PreprocessingResult successfulPreprocessing() {
        return new PreprocessingResult(
                Optional.of(new AiInspectionResponse(ContentReportData.empty(), List.of())),
                Optional.empty());
    }

    private Fixture fixture() {
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentRepository contents = mock(ContentRepository.class);
        ContentMediaRepository media = mock(ContentMediaRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        SelectorsRepository selectors = mock(SelectorsRepository.class);
        InspectionPolicyService policies = mock(InspectionPolicyService.class);
        MediaPreprocessingService preprocessing = mock(MediaPreprocessingService.class);
        AiViolationDetector ai = mock(AiViolationDetector.class);
        ViolationResultMerger merger = mock(ViolationResultMerger.class);
        EvidenceLocationNormalizer normalizer = mock(EvidenceLocationNormalizer.class);
        ViolationReconciliationService reconciliation =
                mock(ViolationReconciliationService.class);
        TaskLeaseTransaction leaseTransaction = mock(TaskLeaseTransaction.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        doAnswer(invocation -> {
            TaskLeaseTransaction.LeaseProtectedWork work = invocation.getArgument(4);
            work.run();
            return null;
        }).when(leaseTransaction).execute(any(), anyLong(), anyLong(), anyLong(), any());

        ContentInspectionExecutionService service = new ContentInspectionExecutionService(
                versions, contents, media, reports, selectors, policies, preprocessing,
                List.of(), ai, merger, normalizer, reconciliation, leaseTransaction,
                transactions, CLOCK);
        return new Fixture(
                versions, contents, media, reports, policies, selectors, preprocessing,
                merger, normalizer, reconciliation, leaseTransaction, transactions, service);
    }

    private Content content(Long lastVersionNo) {
        Content content = Content.builder()
                .selectorsId(7L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("abc123")
                .contentUrl("https://youtu.be/abc123")
                .contentType(com.fuma.hiselectors.content.model.ContentType.LONG_FORM)
                .lastVersionNo(lastVersionNo)
                .build();
        ReflectionTestUtils.setField(content, "id", 10L);
        return content;
    }

    private ContentVersion version(
            Long id, Long versionNo, ContentVersionCreationReason reason) {
        ContentVersion version = ContentVersion.create(
                10L, versionNo, "hash", reason,
                CLOCK.instant().atOffset(ZoneOffset.UTC).toLocalDateTime());
        ReflectionTestUtils.setField(version, "id", id);
        return version;
    }

    private Selectors selectors() {
        return Selectors.builder()
                .selectorsRoleId("SELECTORS")
                .selectorsCode("SEL-1")
                .build();
    }

    private TaskLease lease() {
        return new TaskLease(UUID.randomUUID(), UUID.randomUUID());
    }

    private static final class Fixture {

        private final ContentVersionRepository versions;
        private final ContentRepository contents;
        private final ContentMediaRepository media;
        private final ContentReportRepository reports;
        private final InspectionPolicyService policies;
        private final SelectorsRepository selectors;
        private final MediaPreprocessingService preprocessing;
        private final ViolationResultMerger merger;
        private final EvidenceLocationNormalizer normalizer;
        private final ViolationReconciliationService reconciliation;
        private final TaskLeaseTransaction leaseTransaction;
        private final TransactionTemplate transactions;
        private final ContentInspectionExecutionService service;
        private ContentVersion savedVersion;

        private Fixture(
                ContentVersionRepository versions,
                ContentRepository contents,
                ContentMediaRepository media,
                ContentReportRepository reports,
                InspectionPolicyService policies,
                SelectorsRepository selectors,
                MediaPreprocessingService preprocessing,
                ViolationResultMerger merger,
                EvidenceLocationNormalizer normalizer,
                ViolationReconciliationService reconciliation,
                TaskLeaseTransaction leaseTransaction,
                TransactionTemplate transactions,
                ContentInspectionExecutionService service) {
            this.versions = versions;
            this.contents = contents;
            this.media = media;
            this.reports = reports;
            this.policies = policies;
            this.selectors = selectors;
            this.preprocessing = preprocessing;
            this.merger = merger;
            this.normalizer = normalizer;
            this.reconciliation = reconciliation;
            this.leaseTransaction = leaseTransaction;
            this.transactions = transactions;
            this.service = service;
        }
    }
}
