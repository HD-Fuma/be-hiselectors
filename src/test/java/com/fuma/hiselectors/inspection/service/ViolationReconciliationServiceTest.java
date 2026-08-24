package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.model.ViolationType;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ViolationReconciliationServiceTest {

    private static final Long POLICY_ID = 9L;

    @Test
    void keepsSameViolationAndUpdatesLastDetectedVersion() {
        Fixture fixture = fixture();
        ViolationItem existing = item(fixture.v1, 100L, "욕설");
        existing.confirm();
        existing.requestEdit();
        when(fixture.itemRepository.findAllByContentIdForUpdate(10L))
                .thenReturn(List.of(existing));
        when(fixture.typeRepository.findAllById(any()))
                .thenReturn(List.of(type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE)));

        fixture.service.reconcile(fixture.content, fixture.v2,
                List.of(detected(ViolationTypeCode.ABUSIVE_LANGUAGE)),
                POLICY_ID);

        assertThat(existing.getLastDetectedContentVersionId()).isEqualTo(2L);
        assertThat(existing.getStatus()).isEqualTo(ViolationStatus.EDIT_REQUESTED);
        verify(fixture.historyService).upsert(existing, fixture.v2, POLICY_ID);
        verify(fixture.itemRepository, never()).save(any());
    }

    @Test
    void resolvesOldTypeAndCreatesDifferentType() {
        Fixture fixture = fixture();
        ViolationItem existing = item(fixture.v1, 100L, "욕설");
        when(fixture.itemRepository.findAllByContentIdForUpdate(10L))
                .thenReturn(List.of(existing));
        when(fixture.typeRepository.findAllById(any()))
                .thenReturn(List.of(type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE)));
        when(fixture.typeRepository.findAllByCodeIn(any(Collection.class)))
                .thenReturn(List.of(type(101L, ViolationTypeCode.HATE_DISCRIMINATION)));
        when(fixture.itemRepository.save(any())).thenAnswer(invocation -> {
            ViolationItem created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 51L);
            return created;
        });

        fixture.service.reconcile(fixture.content, fixture.v2,
                List.of(detected(ViolationTypeCode.HATE_DISCRIMINATION)),
                POLICY_ID);

        assertThat(existing.getStatus()).isEqualTo(ViolationStatus.RESOLVED);
        ArgumentCaptor<ViolationItem> captor = ArgumentCaptor.forClass(ViolationItem.class);
        verify(fixture.itemRepository).save(captor.capture());
        assertThat(captor.getValue().getViolationTypeId()).isEqualTo(101L);
        verify(fixture.historyService, never())
                .upsert(eq(existing), any(), any());
        verify(fixture.historyService).upsert(captor.getValue(), fixture.v2, POLICY_ID);
    }

    @Test
    void createsPendingViolationWhenPreviouslyNormalContentChanges() {
        Fixture fixture = fixture();
        when(fixture.itemRepository.findAllByContentIdForUpdate(10L)).thenReturn(List.of());
        when(fixture.typeRepository.findAllById(any())).thenReturn(List.of());
        when(fixture.typeRepository.findAllByCodeIn(any(Collection.class)))
                .thenReturn(List.of(type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE)));
        when(fixture.itemRepository.save(any())).thenAnswer(invocation -> {
            ViolationItem created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "id", 52L);
            return created;
        });

        fixture.service.reconcile(fixture.content, fixture.v2,
                List.of(detected(ViolationTypeCode.ABUSIVE_LANGUAGE)),
                POLICY_ID);

        ArgumentCaptor<ViolationItem> captor = ArgumentCaptor.forClass(ViolationItem.class);
        verify(fixture.itemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ViolationStatus.PENDING);
        assertThat(captor.getValue().getContentId()).isEqualTo(10L);
    }

    @Test
    void reopensResolvedViolationAsPending() {
        Fixture fixture = fixture();
        ViolationItem existing = item(fixture.v1, 100L, "욕설");
        existing.resolve(fixture.v2);
        when(fixture.itemRepository.findAllByContentIdForUpdate(10L))
                .thenReturn(List.of(existing));
        when(fixture.typeRepository.findAllById(any()))
                .thenReturn(List.of(type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE)));

        fixture.service.reconcile(fixture.content, fixture.v3,
                List.of(detected(ViolationTypeCode.ABUSIVE_LANGUAGE)),
                POLICY_ID);

        assertThat(existing.getId()).isEqualTo(20L);
        assertThat(existing.getStatus()).isEqualTo(ViolationStatus.PENDING);
        assertThat(existing.getResolvedContentVersionId()).isNull();
        verify(fixture.itemRepository, never()).save(any());
        verify(fixture.historyService).upsert(existing, fixture.v3, POLICY_ID);
    }

    @Test
    void reopensDismissedViolationAsPending() {
        Fixture fixture = fixture();
        ViolationItem existing = item(fixture.v1, 100L, "욕설");
        existing.dismiss();
        when(fixture.itemRepository.findAllByContentIdForUpdate(10L))
                .thenReturn(List.of(existing));
        when(fixture.typeRepository.findAllById(any()))
                .thenReturn(List.of(type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE)));

        fixture.service.reconcile(fixture.content, fixture.v2,
                List.of(detected(ViolationTypeCode.ABUSIVE_LANGUAGE)),
                POLICY_ID);

        assertThat(existing.getStatus()).isEqualTo(ViolationStatus.PENDING);
        verify(fixture.historyService).upsert(existing, fixture.v2, POLICY_ID);
    }

    @Test
    void keepsDismissedWhenTypeIsNotDetected() {
        Fixture fixture = fixture();
        ViolationItem existing = item(fixture.v1, 100L, "욕설");
        existing.dismiss();
        when(fixture.itemRepository.findAllByContentIdForUpdate(10L))
                .thenReturn(List.of(existing));
        when(fixture.typeRepository.findAllById(any()))
                .thenReturn(List.of(type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE)));

        fixture.service.reconcile(fixture.content, fixture.v2, List.of(), POLICY_ID);

        assertThat(existing.getStatus()).isEqualTo(ViolationStatus.DISMISSED);
        verify(fixture.historyService, never()).upsert(any(), any(), any());
    }

    private Fixture fixture() {
        ViolationItemRepository itemRepository = mock(ViolationItemRepository.class);
        ViolationTypeRepository typeRepository = mock(ViolationTypeRepository.class);
        ViolationEvidenceHistoryService historyService = mock(ViolationEvidenceHistoryService.class);
        PenaltyService penaltyService = mock(PenaltyService.class);
        Content content = Content.create(7L, SnsPlatform.INSTAGRAM, "url", "POST");
        ReflectionTestUtils.setField(content, "id", 10L);
        ContentVersion v1 = ContentVersion.create(10L, 1L, "v1");
        ReflectionTestUtils.setField(v1, "id", 1L);
        ContentVersion v2 = ContentVersion.create(10L, 2L, "v2");
        ReflectionTestUtils.setField(v2, "id", 2L);
        ContentVersion v3 = ContentVersion.create(10L, 3L, "v3");
        ReflectionTestUtils.setField(v3, "id", 3L);
        return new Fixture(itemRepository, typeRepository, historyService,
                new ViolationReconciliationService(
                        itemRepository, typeRepository, historyService, penaltyService),
                content, v1, v2, v3);
    }

    private ViolationItem item(ContentVersion version, Long typeId, String reason) {
        ViolationItem existing = ViolationItem.pending(version, typeId, evidence(reason));
        ReflectionTestUtils.setField(existing, "id", 20L);
        return existing;
    }

    private ViolationType type(Long id, ViolationTypeCode code) {
        ViolationType type = ViolationType.create(code, code.name());
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    private DetectedViolation detected(ViolationTypeCode code) {
        return new DetectedViolation(code, evidence(code.name()));
    }

    private ViolationEvidence evidence(String reason) {
        return new ViolationEvidence(reason, 1.0, List.of(), EvidenceSource.AI);
    }

    private record Fixture(
            ViolationItemRepository itemRepository,
            ViolationTypeRepository typeRepository,
            ViolationEvidenceHistoryService historyService,
            ViolationReconciliationService service,
            Content content,
            ContentVersion v1,
            ContentVersion v2,
            ContentVersion v3) {
    }
}
