package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.model.ViolationType;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContentDetailQueryServiceTest {

    @Test
    void returnsMediaAndClosedViolationFromLatestReportPolicySnapshot() {
        ContentRepository contents = mock(ContentRepository.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentMediaRepository mediaRepository = mock(ContentMediaRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        ViolationItemRepository items = mock(ViolationItemRepository.class);
        ViolationTypeRepository types = mock(ViolationTypeRepository.class);
        ViolationEvidenceHistoryRepository histories =
                mock(ViolationEvidenceHistoryRepository.class);
        ContentDetailQueryService service = new ContentDetailQueryService(
                contents, versions, mediaRepository, reports, items, types, histories);

        Content content = Content.create(
                7L, SnsPlatform.YOUTUBE, "https://youtu.be/abc123", "LONG_FORM");
        ReflectionTestUtils.setField(content, "id", 10L);
        ContentVersion version = ContentVersion.create(
                10L, 2L, "hash", ContentVersionCreationReason.EXTRACTION_CHANGE,
                LocalDateTime.of(2026, 8, 24, 1, 0));
        ReflectionTestUtils.setField(version, "id", 20L);
        ContentMedia text = ContentMedia.create(
                20L, MediaType.TEXT, null, null, 0, Map.of("text", "본문"));
        ReflectionTestUtils.setField(text, "id", 31L);
        ContentMedia video = ContentMedia.create(
                20L, MediaType.VIDEO, null, "abc123", 1, Map.of(
                        "schemaVersion", "1.0",
                        "stt", Map.of(
                                "language", "ko",
                                "segments", List.of(Map.of(
                                        "segmentId", "stt-001",
                                        "startMs", 100,
                                        "endMs", 900,
                                        "text", "spoken evidence"))),
                        "ocr", Map.of("segments", List.of()),
                        "visual", Map.of("segments", List.of())));
        ReflectionTestUtils.setField(video, "id", 32L);

        ContentReport report = ContentReport.create(
                20L,
                new ContentReportAnalysis(
                        new ContentReportAnalysis.Overview(
                                "summary", "purpose", "flow", "assessment"),
                        new ContentReportAnalysis.Insight(
                                "review", "calm", List.of("clear"), List.of(),
                                List.of(), false, List.of("brand-a"))),
                9L, Map.of("responseModel", "gemini"));
        ReflectionTestUtils.setField(report, "id", 40L);
        ViolationEvidence evidence = new ViolationEvidence(
                "stored evidence", 0.9, List.of(new EvidenceLocation(
                        32L, MediaType.VIDEO, EvidenceTargetKind.STT_SEGMENT,
                        EvidenceCoordinateSpace.CONTENT_MEDIA_SEGMENT,
                        "stt-001", null, null, "spoken")), EvidenceSource.AI);
        ViolationItem item = ViolationItem.pending(version, 100L, evidence);
        ReflectionTestUtils.setField(item, "id", 50L);
        ContentVersion resolvedVersion = ContentVersion.create(10L, 3L, "next");
        ReflectionTestUtils.setField(resolvedVersion, "id", 21L);
        item.resolve(resolvedVersion);
        ViolationEvidenceHistory history = ViolationEvidenceHistory.create(
                50L, 20L, 9L, 40L, evidence,
                LocalDateTime.of(2026, 8, 24, 1, 5));
        ReflectionTestUtils.setField(history, "id", 60L);
        ViolationType type = ViolationType.create(
                ViolationTypeCode.ABUSIVE_LANGUAGE, "욕설");
        ReflectionTestUtils.setField(type, "id", 100L);

        when(contents.findById(10L)).thenReturn(Optional.of(content));
        when(versions.findAllByContentIdOrderByVersionNoDesc(10L))
                .thenReturn(List.of(version));
        when(mediaRepository.findByContentVersionIdOrderBySequenceNoAsc(20L))
                .thenReturn(List.of(text, video));
        when(reports.findFirstByContentVersionIdOrderByIdDesc(20L))
                .thenReturn(Optional.of(report));
        when(histories.findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(20L, 9L))
                .thenReturn(List.of(history));
        when(items.findAllById(List.of(50L))).thenReturn(List.of(item));
        when(types.findAllById(List.of(100L))).thenReturn(List.of(type));

        var result = service.getLatest(10L);

        assertThat(result.selectedVersion().creationReason())
                .isEqualTo(ContentVersionCreationReason.EXTRACTION_CHANGE);
        assertThat(result.selectedVersion().media())
                .extracting(media -> media.contentMediaId(), media -> media.text())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(31L, "본문"),
                        org.assertj.core.groups.Tuple.tuple(32L, null));
        assertThat(result.selectedVersion().media().get(1).body())
                .containsEntry("schemaVersion", "1.0")
                .containsKeys("stt", "ocr", "visual");
        assertThat(result.selectedVersion().contentReport().analysis().insight().contentStyle())
                .isEqualTo("review");
        assertThat(result.selectedVersion().contentReport().executionMetadata())
                .containsEntry("responseModel", "gemini");
        assertThat(result.selectedVersion().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.violationEvidenceHistoryId()).isEqualTo(60L);
            assertThat(violation.contentReportId()).isEqualTo(40L);
            assertThat(violation.inspectionPolicyId()).isEqualTo(9L);
            assertThat(violation.currentStatus()).isEqualTo(ViolationStatus.RESOLVED);
            assertThat(violation.evidence()).isEqualTo(evidence);
            assertThat(violation.resolvedLocations()).singleElement().satisfies(location -> {
                assertThat(location.targetKind()).isEqualTo(EvidenceTargetKind.STT_SEGMENT);
                assertThat(location.segmentId()).isEqualTo("stt-001");
                assertThat(location.startMs()).isEqualTo(100L);
                assertThat(location.endMs()).isEqualTo(900L);
                assertThat(location.bbox()).isNull();
            });
            assertThat(violation.detectedAt())
                    .isEqualTo(LocalDateTime.of(2026, 8, 24, 1, 5));
        });
        verify(histories)
                .findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(20L, 9L);
    }

    @Test
    void returnsAllPolicyHistoriesForHistoricalVersion() {
        ContentRepository contents = mock(ContentRepository.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentMediaRepository mediaRepository = mock(ContentMediaRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        ViolationItemRepository items = mock(ViolationItemRepository.class);
        ViolationTypeRepository types = mock(ViolationTypeRepository.class);
        ViolationEvidenceHistoryRepository histories =
                mock(ViolationEvidenceHistoryRepository.class);
        ContentDetailQueryService service = new ContentDetailQueryService(
                contents, versions, mediaRepository, reports, items, types, histories);

        Content content = Content.create(
                7L, SnsPlatform.INSTAGRAM, "https://instagram.com/p/old", "POST");
        ReflectionTestUtils.setField(content, "id", 10L);
        ContentVersion historical = ContentVersion.create(10L, 1L, "old");
        ReflectionTestUtils.setField(historical, "id", 20L);
        ContentVersion latest = ContentVersion.create(10L, 2L, "latest");
        ReflectionTestUtils.setField(latest, "id", 21L);
        ContentReport report = ContentReport.create(
                20L, ContentReportData.empty(), 9L);
        ViolationEvidence evidenceA = new ViolationEvidence(
                "정책 A 근거", 0.8, List.of(), EvidenceSource.RULE);
        ViolationEvidence evidenceB = new ViolationEvidence(
                "정책 B 근거", 0.9, List.of(), EvidenceSource.AI);
        ViolationItem item = ViolationItem.pending(historical, 100L, evidenceB);
        ReflectionTestUtils.setField(item, "id", 50L);
        ViolationEvidenceHistory historyA = ViolationEvidenceHistory.create(
                50L, 20L, 8L, evidenceA, LocalDateTime.of(2026, 8, 23, 1, 0));
        ReflectionTestUtils.setField(historyA, "id", 60L);
        ViolationEvidenceHistory historyB = ViolationEvidenceHistory.create(
                50L, 20L, 9L, evidenceB, LocalDateTime.of(2026, 8, 24, 1, 0));
        ReflectionTestUtils.setField(historyB, "id", 61L);
        ViolationType type = ViolationType.create(
                ViolationTypeCode.ABUSIVE_LANGUAGE, "욕설");
        ReflectionTestUtils.setField(type, "id", 100L);

        when(contents.findById(10L)).thenReturn(Optional.of(content));
        when(versions.findAllByContentIdOrderByVersionNoDesc(10L))
                .thenReturn(List.of(latest, historical));
        when(versions.findByIdAndContentId(20L, 10L)).thenReturn(Optional.of(historical));
        when(mediaRepository.findByContentVersionIdOrderBySequenceNoAsc(20L))
                .thenReturn(List.of());
        when(reports.findFirstByContentVersionIdOrderByIdDesc(20L))
                .thenReturn(Optional.of(report));
        when(histories.findAllByContentVersionIdOrderByDetectedAtAscIdAsc(20L))
                .thenReturn(List.of(historyA, historyB));
        when(items.findAllById(List.of(50L))).thenReturn(List.of(item));
        when(types.findAllById(List.of(100L))).thenReturn(List.of(type));

        var result = service.getVersion(10L, 20L);

        assertThat(result.selectedVersion().violations())
                .extracting(
                        violation -> violation.inspectionPolicyId(),
                        violation -> violation.evidence().reason())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(8L, "정책 A 근거"),
                        org.assertj.core.groups.Tuple.tuple(9L, "정책 B 근거"));
        verify(histories).findAllByContentVersionIdOrderByDetectedAtAscIdAsc(20L);
        verify(histories, never())
                .findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(20L, 9L);
    }

    @Test
    void returnsPriorEvidenceForCorrectionWaitingForReview() {
        ContentRepository contents = mock(ContentRepository.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentMediaRepository mediaRepository = mock(ContentMediaRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        ViolationItemRepository items = mock(ViolationItemRepository.class);
        ViolationTypeRepository types = mock(ViolationTypeRepository.class);
        ViolationEvidenceHistoryRepository histories =
                mock(ViolationEvidenceHistoryRepository.class);
        ContentDetailQueryService service = new ContentDetailQueryService(
                contents, versions, mediaRepository, reports, items, types, histories);

        Content content = Content.create(
                7L, SnsPlatform.INSTAGRAM, "https://instagram.com/p/corrected", "POST");
        ReflectionTestUtils.setField(content, "id", 10L);
        ContentVersion detectedVersion = ContentVersion.create(10L, 1L, "old");
        ReflectionTestUtils.setField(detectedVersion, "id", 19L);
        ContentVersion correctedVersion = ContentVersion.create(10L, 2L, "clean");
        ReflectionTestUtils.setField(correctedVersion, "id", 20L);
        ContentReport report = ContentReport.create(20L, ContentReportData.empty(), 9L);
        ViolationEvidence evidence = new ViolationEvidence(
                "prior violation", 0.9, List.of(), EvidenceSource.AI);
        ViolationItem item = ViolationItem.pending(detectedVersion, 100L, evidence);
        ReflectionTestUtils.setField(item, "id", 50L);
        item.confirm();
        item.requestEdit();
        item.awaitCorrectionReview(correctedVersion);
        ViolationEvidenceHistory history = ViolationEvidenceHistory.create(
                50L, 19L, 8L, evidence, LocalDateTime.of(2026, 8, 24, 1, 0));
        ReflectionTestUtils.setField(history, "id", 60L);
        ViolationType type = ViolationType.create(
                ViolationTypeCode.ABUSIVE_LANGUAGE, "abusive language");
        ReflectionTestUtils.setField(type, "id", 100L);

        when(contents.findById(10L)).thenReturn(Optional.of(content));
        when(versions.findAllByContentIdOrderByVersionNoDesc(10L))
                .thenReturn(List.of(correctedVersion, detectedVersion));
        when(mediaRepository.findByContentVersionIdOrderBySequenceNoAsc(20L))
                .thenReturn(List.of());
        when(reports.findFirstByContentVersionIdOrderByIdDesc(20L))
                .thenReturn(Optional.of(report));
        when(histories.findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(20L, 9L))
                .thenReturn(List.of());
        when(items.findAllByResolvedContentVersionIdAndStatusOrderByIdAsc(
                20L, ViolationStatus.CORRECTION_REVIEW_PENDING))
                .thenReturn(List.of(item));
        when(histories.findFirstByViolationItemIdOrderByDetectedAtDescIdDesc(50L))
                .thenReturn(Optional.of(history));
        when(items.findAllById(List.of(50L))).thenReturn(List.of(item));
        when(types.findAllById(List.of(100L))).thenReturn(List.of(type));

        var result = service.getLatest(10L);

        assertThat(result.selectedVersion().inspectionDecision()).isNull();
        assertThat(result.selectedVersion().violations()).singleElement().satisfies(violation -> {
            assertThat(violation.currentStatus())
                    .isEqualTo(ViolationStatus.CORRECTION_REVIEW_PENDING);
            assertThat(violation.violationEvidenceHistoryId()).isEqualTo(60L);
            assertThat(violation.evidence()).isEqualTo(evidence);
        });
    }
}
