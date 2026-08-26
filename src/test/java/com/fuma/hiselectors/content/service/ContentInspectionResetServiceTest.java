package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContentInspectionResetServiceTest {

    @Test
    void resetsConfirmedCurrentGenerationVersionsAndViolationDecisions() {
        GenerationService generations = mock(GenerationService.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        ViolationEvidenceHistoryRepository histories = mock(ViolationEvidenceHistoryRepository.class);
        ViolationItemRepository violations = mock(ViolationItemRepository.class);
        ContentInspectionResetService service = new ContentInspectionResetService(
                generations, versions, reports, histories, violations);

        Generation generation = Generation.builder().generationName("1기").build();
        ReflectionTestUtils.setField(generation, "id", 3L);
        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ReflectionTestUtils.setField(version, "id", 100L);
        version.startInspection();
        version.completeInspection(LocalDateTime.now());
        version.confirmInspection(ContentInspectionDecision.APPROVED);
        ViolationItem item = ViolationItem.pending(
                version, 21L, new ViolationEvidence("근거", 1.0, List.of(), EvidenceSource.AI));
        ReflectionTestUtils.setField(item, "id", 31L);
        item.dismiss();
        ContentReport report = ContentReport.create(100L, ContentReportData.empty(), 7L);
        ViolationEvidenceHistory history = ViolationEvidenceHistory.create(
                31L, 100L, 7L, item.getEvidence(), LocalDateTime.now());

        when(generations.getCurrentActivity()).thenReturn(generation);
        when(versions.findConfirmedCurrentByGenerationIdForUpdate(3L))
                .thenReturn(List.of(version));
        when(reports.findFirstByContentVersionIdOrderByIdDesc(100L))
                .thenReturn(Optional.of(report));
        when(histories.findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(100L, 7L))
                .thenReturn(List.of(history));
        when(violations.findAllByIdInForUpdate(List.of(31L))).thenReturn(List.of(item));

        var response = service.resetCurrentGeneration(
                ContentInspectionResetService.CONFIRMATION);

        assertThat(response.resetVersionCount()).isEqualTo(1);
        assertThat(response.resetViolationCount()).isEqualTo(1);
        assertThat(version.getInspectionDecision()).isNull();
        assertThat(item.getStatus()).isEqualTo(ViolationStatus.PENDING);
    }

    @Test
    void rejectsWrongConfirmationBeforeReadingData() {
        GenerationService generations = mock(GenerationService.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        ViolationEvidenceHistoryRepository histories = mock(ViolationEvidenceHistoryRepository.class);
        ViolationItemRepository violations = mock(ViolationItemRepository.class);
        ContentInspectionResetService service = new ContentInspectionResetService(
                generations, versions, reports, histories, violations);

        assertThatThrownBy(() -> service.resetCurrentGeneration("RESET"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(generations, versions, reports, histories, violations);
    }
}
