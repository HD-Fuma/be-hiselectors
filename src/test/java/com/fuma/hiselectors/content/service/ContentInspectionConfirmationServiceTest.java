package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationRequest;
import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationRequest.ViolationDecision;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
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

class ContentInspectionConfirmationServiceTest {

    @Test
    void approvesVersionWithoutCandidates() {
        Fixture fixture = fixture(List.of());

        var response = fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of()));

        assertThat(response.updatedCount()).isZero();
        assertThat(fixture.version.getInspectionDecision())
                .isEqualTo(ContentInspectionDecision.APPROVED);
    }

    @Test
    void approvesWhenEveryPendingItemIsDismissed() {
        ViolationItem first = item(21L);
        ViolationItem second = item(22L);
        Fixture fixture = fixture(List.of(first, second));

        var response = fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED),
                        target(22L, ViolationStatus.DISMISSED))));

        assertThat(response.updatedCount()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        assertThat(second.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
    }

    @Test
    void rejectsWhenAtLeastOneItemIsConfirmed() {
        ViolationItem first = item(21L);
        ViolationItem second = item(22L);
        Fixture fixture = fixture(List.of(first, second));

        var response = fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.REJECTED, List.of(
                        target(21L, ViolationStatus.VIOLATION_CONFIRMED),
                        target(22L, ViolationStatus.DISMISSED))));

        assertThat(response.updatedCount()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(ViolationStatus.VIOLATION_CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
    }

    @Test
    void rejectsApprovedDecisionContainingConfirmedItem() {
        Fixture fixture = fixture(List.of(item(21L)));

        assertInvalid(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.VIOLATION_CONFIRMED)))));
    }

    @Test
    void rejectsRejectedDecisionContainingOnlyDismissedItems() {
        Fixture fixture = fixture(List.of(item(21L)));

        assertInvalid(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.REJECTED, List.of(
                        target(21L, ViolationStatus.DISMISSED)))));
    }

    @Test
    void rejectsMissingAdditionalAndDuplicatePendingIds() {
        Fixture missing = fixture(List.of(item(21L), item(22L)));
        assertInvalid(() -> missing.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED)))));

        Fixture additional = fixture(List.of(item(21L)));
        assertInvalid(() -> additional.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED),
                        target(999L, ViolationStatus.DISMISSED)))));

        Fixture duplicate = fixture(List.of(item(21L)));
        assertInvalid(() -> duplicate.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED),
                        target(21L, ViolationStatus.DISMISSED)))));
    }

    @Test
    void rejectsAlreadyConfirmedVersion() {
        Fixture fixture = fixture(List.of());
        fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of()));

        assertThatThrownBy(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED));
    }

    @Test
    void rejectsVersionOwnedByAnotherContentAsNotFound() {
        Fixture fixture = fixture(List.of());

        assertThatThrownBy(() -> fixture.service.confirm(11L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONTENT_VERSION_NOT_FOUND));
    }

    @Test
    void rejectsUnsupportedPendingTargetStatus() {
        Fixture fixture = fixture(List.of(item(21L)));

        assertInvalid(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.PENDING)))));
    }

    private Fixture fixture(List<ViolationItem> items) {
        ContentRepository contents = mock(ContentRepository.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        ViolationEvidenceHistoryRepository histories =
                mock(ViolationEvidenceHistoryRepository.class);
        ViolationItemRepository violations = mock(ViolationItemRepository.class);

        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ReflectionTestUtils.setField(version, "id", 100L);
        version.startInspection();
        version.completeInspection(LocalDateTime.now());
        ContentReport report = ContentReport.create(
                100L, ContentReportData.empty(), 7L);

        when(contents.existsById(10L)).thenReturn(true);
        when(contents.existsById(11L)).thenReturn(true);
        when(versions.findByIdForUpdate(100L)).thenReturn(Optional.of(version));
        when(reports.findFirstByContentVersionIdOrderByIdDesc(100L))
                .thenReturn(Optional.of(report));
        List<ViolationEvidenceHistory> history = items.stream()
                .map(item -> ViolationEvidenceHistory.create(
                        item.getId(), 100L, 7L, evidence(), LocalDateTime.now()))
                .toList();
        when(histories.findAllByContentVersionIdAndInspectionPolicyIdOrderByIdAsc(100L, 7L))
                .thenReturn(history);
        if (!items.isEmpty()) {
            when(violations.findAllByIdInForUpdate(
                    items.stream().map(ViolationItem::getId).toList()))
                    .thenReturn(items);
        }
        return new Fixture(new ContentInspectionConfirmationService(
                contents, versions, reports, histories, violations), version);
    }

    private ViolationItem item(Long id) {
        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ReflectionTestUtils.setField(version, "id", 100L);
        ViolationItem item = ViolationItem.pending(version, id + 100L, evidence());
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private ViolationEvidence evidence() {
        return new ViolationEvidence("근거", 1.0, List.of(), EvidenceSource.AI);
    }

    private ContentInspectionConfirmationRequest request(
            ContentInspectionDecision decision,
            List<ViolationDecision> violations) {
        return new ContentInspectionConfirmationRequest(decision, violations);
    }

    private ViolationDecision target(Long id, ViolationStatus status) {
        return new ViolationDecision(id, status);
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CONTENT_INSPECTION_CONFIRMATION));
    }

    private record Fixture(
            ContentInspectionConfirmationService service,
            ContentVersion version) {
    }
}
