package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationRequest;
import com.fuma.hiselectors.content.dto.ContentInspectionConfirmationRequest.ViolationDecision;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentType;
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
import com.fuma.hiselectors.penalty.service.PenaltyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ContentInspectionConfirmationServiceTest {

    @Test
    void approvesVersionWithoutCandidates() {
        Fixture fixture = fixture(List.of());

        var response = fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of()), "admin");

        assertThat(response.updatedCount()).isZero();
        assertThat(fixture.version.getInspectionDecision())
                .isEqualTo(ContentInspectionDecision.APPROVED);
        verify(fixture.penaltyService).releaseIfEligible(5L);
        verify(fixture.eventPublisher, never()).publishEvent(
                org.mockito.ArgumentMatchers.any(ContentViolationConfirmedEvent.class));
    }

    @Test
    void approvesWhenEveryPendingItemIsDismissed() {
        ViolationItem first = item(21L);
        ViolationItem second = item(22L);
        Fixture fixture = fixture(List.of(first, second));

        var response = fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED),
                        target(22L, ViolationStatus.DISMISSED))), "admin");

        assertThat(response.updatedCount()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        assertThat(second.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        verify(fixture.penaltyService, never()).activateIfAbsent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(fixture.penaltyService).releaseIfEligible(5L);
    }

    @Test
    void rejectsWhenAtLeastOneItemIsConfirmed() {
        ViolationItem first = item(21L);
        ViolationItem second = item(22L);
        Fixture fixture = fixture(List.of(first, second));

        var response = fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.REJECTED, List.of(
                        target(21L, ViolationStatus.VIOLATION_CONFIRMED),
                        target(22L, ViolationStatus.DISMISSED))), "admin");

        assertThat(response.updatedCount()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(ViolationStatus.VIOLATION_CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        assertThat(fixture.version.getInspectionDecision())
                .isEqualTo(ContentInspectionDecision.REJECTED);
        verify(fixture.penaltyService).activateIfAbsent(
                5L, 100L, 121L, "근거", "admin");
        verify(fixture.penaltyService).releaseIfEligible(5L);
        ArgumentCaptor<ContentViolationConfirmedEvent> eventCaptor =
                ArgumentCaptor.forClass(ContentViolationConfirmedEvent.class);
        verify(fixture.eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(
                new ContentViolationConfirmedEvent("admin", 10L, 5L, List.of(21L)));
    }

    @Test
    void stillPublishesNotificationWhenActivePenaltyAlreadyExists() {
        ViolationItem item = item(21L);
        Fixture fixture = fixture(List.of(item));
        when(fixture.penaltyService.activateIfAbsent(
                5L, 100L, 121L, "근거", "admin"))
                .thenReturn(false);

        fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.REJECTED, List.of(
                        target(21L, ViolationStatus.VIOLATION_CONFIRMED))), "admin");

        verify(fixture.penaltyService).activateIfAbsent(
                5L, 100L, 121L, "근거", "admin");
        verify(fixture.eventPublisher).publishEvent(
                new ContentViolationConfirmedEvent("admin", 10L, 5L, List.of(21L)));
    }

    @Test
    void rejectsApprovedDecisionContainingConfirmedItem() {
        Fixture fixture = fixture(List.of(item(21L)));

        assertInvalid(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.VIOLATION_CONFIRMED))), "admin"));
    }

    @Test
    void rejectsRejectedDecisionContainingOnlyDismissedItems() {
        Fixture fixture = fixture(List.of(item(21L)));

        assertInvalid(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.REJECTED, List.of(
                        target(21L, ViolationStatus.DISMISSED))), "admin"));
    }

    @Test
    void rejectsMissingAdditionalAndDuplicatePendingIds() {
        Fixture missing = fixture(List.of(item(21L), item(22L)));
        assertInvalid(() -> missing.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED))), "admin"));

        Fixture additional = fixture(List.of(item(21L)));
        assertInvalid(() -> additional.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED),
                        target(999L, ViolationStatus.DISMISSED))), "admin"));

        Fixture duplicate = fixture(List.of(item(21L)));
        assertInvalid(() -> duplicate.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.DISMISSED),
                        target(21L, ViolationStatus.DISMISSED))), "admin"));
    }

    @Test
    void rejectsAlreadyConfirmedVersion() {
        Fixture fixture = fixture(List.of());
        fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of()), "admin");

        assertThatThrownBy(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of()), "admin"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONTENT_INSPECTION_ALREADY_CONFIRMED));
    }

    @Test
    void rejectsVersionOwnedByAnotherContentAsNotFound() {
        Fixture fixture = fixture(List.of());

        assertThatThrownBy(() -> fixture.service.confirm(11L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of()), "admin"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONTENT_VERSION_NOT_FOUND));
    }

    @Test
    void processesRemainingPendingItemsAfterIndividualViolationConfirmation() {
        ViolationItem confirmed = item(21L);
        ViolationItem pending = item(22L);
        Fixture fixture = fixture(List.of(confirmed, pending));
        confirmed.confirm();
        fixture.version.confirmInspection(ContentInspectionDecision.REJECTED);

        var response = fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.REJECTED, List.of(
                        target(22L, ViolationStatus.DISMISSED))), "admin");

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(confirmed.getStatus()).isEqualTo(ViolationStatus.VIOLATION_CONFIRMED);
        assertThat(pending.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        verify(fixture.eventPublisher, never()).publishEvent(
                org.mockito.ArgumentMatchers.any(ContentViolationConfirmedEvent.class));
    }

    @Test
    void rejectsInspectionDecisionForHistoricalVersion() {
        Fixture fixture = fixture(List.of());
        ReflectionTestUtils.setField(fixture.content, "lastVersionNo", 2L);

        assertThatThrownBy(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of()), "admin"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONTENT_VERSION_NOT_FOUND));
    }

    @Test
    void rejectsUnsupportedPendingTargetStatus() {
        Fixture fixture = fixture(List.of(item(21L)));

        assertInvalid(() -> fixture.service.confirm(10L, 100L,
                request(ContentInspectionDecision.APPROVED, List.of(
                        target(21L, ViolationStatus.PENDING))), "admin"));
    }

    private Fixture fixture(List<ViolationItem> items) {
        ContentRepository contents = mock(ContentRepository.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentReportRepository reports = mock(ContentReportRepository.class);
        ViolationEvidenceHistoryRepository histories =
                mock(ViolationEvidenceHistoryRepository.class);
        ViolationItemRepository violations = mock(ViolationItemRepository.class);
        PenaltyService penaltyService = mock(PenaltyService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        Content content = Content.builder()
                .selectorsId(5L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("post-1")
                .contentUrl("https://instagram.com/p/post-1")
                .contentType(ContentType.FEED)
                .build();
        ReflectionTestUtils.setField(content, "id", 10L);
        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ReflectionTestUtils.setField(version, "id", 100L);
        version.startInspection();
        version.completeInspection(LocalDateTime.now());
        ContentReport report = ContentReport.create(
                100L, ContentReportData.empty(), 7L);

        when(contents.findByIdForUpdate(10L)).thenReturn(Optional.of(content));
        when(contents.findByIdForUpdate(11L)).thenReturn(Optional.of(content));
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
                contents, versions, reports, histories, violations,
                new ContentViolationDecisionProcessor(penaltyService), penaltyService,
                eventPublisher),
                content, version, penaltyService, eventPublisher);
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
            Content content,
            ContentVersion version,
            PenaltyService penaltyService,
            ApplicationEventPublisher eventPublisher) {
    }
}
