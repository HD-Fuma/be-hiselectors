package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.content.service.ContentViolationDecisionProcessor;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ViolationConfirmationWriterTest {

    @Test
    void penaltyUsesLastDetectedContentVersionAndEvidenceReason() {
        ViolationItemRepository violationRepository = mock(ViolationItemRepository.class);
        ContentRepository contentRepository = mock(ContentRepository.class);
        ContentVersionRepository versionRepository = mock(ContentVersionRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PenaltyService penaltyService = mock(PenaltyService.class);

        ContentVersion originalVersion = ContentVersion.create(20L, 1L, "original");
        ReflectionTestUtils.setField(originalVersion, "id", 100L);
        ViolationItem item = ViolationItem.pending(
                originalVersion, 4L, evidence("최초 탐지 사유"));
        ReflectionTestUtils.setField(item, "id", 10L);
        ContentVersion latestVersion = ContentVersion.create(20L, 1L, "latest");
        ReflectionTestUtils.setField(latestVersion, "id", 101L);
        latestVersion.startInspection();
        latestVersion.completeInspection(java.time.LocalDateTime.now());
        item.redetectForReview(latestVersion, evidence("최신 탐지 사유"));

        Content content = mock(Content.class);
        when(content.getId()).thenReturn(20L);
        when(content.getSelectorsId()).thenReturn(9L);
        when(content.getLastVersionNo()).thenReturn(1L);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(9L);
        when(selectors.getUserId()).thenReturn(7L);
        when(selectors.getSelectorsNickname()).thenReturn("셀렉터");
        when(violationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(item));
        when(contentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(content));
        when(versionRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(latestVersion));
        when(selectorsRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(selectors));
        when(penaltyService.activateIfAbsent(
                9L, 101L, 4L, "최신 탐지 사유", "admin"))
                .thenReturn(true);

        ViolationConfirmationWriter writer = new ViolationConfirmationWriter(
                violationRepository, contentRepository, versionRepository, selectorsRepository,
                new ContentViolationDecisionProcessor(penaltyService), penaltyService);

        var result = writer.prepare(10L, "admin");

        assertThat(item.getStatus()).isEqualTo(ViolationStatus.VIOLATION_CONFIRMED);
        assertThat(latestVersion.getInspectionDecision())
                .isEqualTo(ContentInspectionDecision.REJECTED);
        assertThat(result.reason()).isEqualTo("최신 탐지 사유");
        assertThat(result.penaltyCreated()).isTrue();
        verify(penaltyService).activateIfAbsent(
                9L, 101L, 4L, "최신 탐지 사유", "admin");
    }

    @Test
    void rejectsViolationDecisionForHistoricalVersion() {
        ViolationItemRepository violations = mock(ViolationItemRepository.class);
        ContentRepository contents = mock(ContentRepository.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        SelectorsRepository selectors = mock(SelectorsRepository.class);
        PenaltyService penalties = mock(PenaltyService.class);
        ContentVersion historical = ContentVersion.create(20L, 1L, "old");
        ReflectionTestUtils.setField(historical, "id", 101L);
        ViolationItem item = ViolationItem.pending(historical, 4L, evidence("reason"));
        ReflectionTestUtils.setField(item, "id", 10L);
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(20L);
        when(content.getLastVersionNo()).thenReturn(2L);
        when(violations.findByIdForUpdate(10L)).thenReturn(Optional.of(item));
        when(contents.findByIdForUpdate(20L)).thenReturn(Optional.of(content));
        when(versions.findByIdForUpdate(101L)).thenReturn(Optional.of(historical));
        ViolationConfirmationWriter writer = new ViolationConfirmationWriter(
                violations, contents, versions, selectors,
                new ContentViolationDecisionProcessor(penalties), penalties);

        assertThatThrownBy(() -> writer.prepare(10L, "admin"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(
                                ErrorCode.HISTORICAL_CONTENT_VERSION_INSPECTION_NOT_ALLOWED));
    }

    private ViolationEvidence evidence(String reason) {
        return new ViolationEvidence(reason, 1.0, List.of(), EvidenceSource.AI);
    }
}
