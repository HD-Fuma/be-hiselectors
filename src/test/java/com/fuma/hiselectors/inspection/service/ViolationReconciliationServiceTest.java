package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
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

    @Test
    void keepsSameViolationAndUpdatesLastDetectedVersion() {
        Fixture fixture = fixture();
        ViolationItem existing = ViolationItem.pending(
                fixture.v1, 100L, evidence("욕설"));
        existing.confirm();
        existing.requestEdit();
        ViolationType abusive = type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE);
        when(fixture.itemRepository.findOpenByContentIdForUpdate(any(), any()))
                .thenReturn(List.of(existing));
        when(fixture.typeRepository.findAllById(any())).thenReturn(List.of(abusive));

        fixture.service.reconcile(fixture.content, fixture.v2,
                List.of(detected(ViolationTypeCode.ABUSIVE_LANGUAGE)));

        assertThat(existing.getLastDetectedContentVersionId()).isEqualTo(2L);
        assertThat(existing.getStatus()).isEqualTo(ViolationStatus.EDIT_REQUESTED);
    }

    @Test
    void resolvesOldTypeAndCreatesDifferentType() {
        Fixture fixture = fixture();
        ViolationItem existing = ViolationItem.pending(
                fixture.v1, 100L, evidence("욕설"));
        ViolationType abusive = type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE);
        ViolationType hate = type(101L, ViolationTypeCode.HATE_DISCRIMINATION);
        when(fixture.itemRepository.findOpenByContentIdForUpdate(any(), any()))
                .thenReturn(List.of(existing));
        when(fixture.typeRepository.findAllById(any())).thenReturn(List.of(abusive));
        when(fixture.typeRepository.findAllByCodeIn(any(Collection.class)))
                .thenReturn(List.of(hate));

        fixture.service.reconcile(fixture.content, fixture.v2,
                List.of(detected(ViolationTypeCode.HATE_DISCRIMINATION)));

        assertThat(existing.getStatus()).isEqualTo(ViolationStatus.RESOLVED);
        ArgumentCaptor<ViolationItem> captor = ArgumentCaptor.forClass(ViolationItem.class);
        verify(fixture.itemRepository).save(captor.capture());
        assertThat(captor.getValue().getViolationTypeId()).isEqualTo(101L);
    }

    @Test
    void createsPendingViolationWhenPreviouslyNormalContentChanges() {
        Fixture fixture = fixture();
        ViolationType abusive = type(100L, ViolationTypeCode.ABUSIVE_LANGUAGE);
        when(fixture.itemRepository.findOpenByContentIdForUpdate(any(), any()))
                .thenReturn(List.of());
        when(fixture.typeRepository.findAllById(any())).thenReturn(List.of());
        when(fixture.typeRepository.findAllByCodeIn(any(Collection.class)))
                .thenReturn(List.of(abusive));

        fixture.service.reconcile(fixture.content, fixture.v2,
                List.of(detected(ViolationTypeCode.ABUSIVE_LANGUAGE)));

        ArgumentCaptor<ViolationItem> captor = ArgumentCaptor.forClass(ViolationItem.class);
        verify(fixture.itemRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ViolationStatus.PENDING);
    }

    private Fixture fixture() {
        ViolationItemRepository itemRepository = mock(ViolationItemRepository.class);
        ViolationTypeRepository typeRepository = mock(ViolationTypeRepository.class);
        PenaltyService penaltyService = mock(PenaltyService.class);
        Content content = Content.create(7L, SnsPlatform.INSTAGRAM, "url", "POST");
        ReflectionTestUtils.setField(content, "id", 10L);
        ContentVersion v1 = ContentVersion.create(10L, 1L, "v1");
        ReflectionTestUtils.setField(v1, "id", 1L);
        ContentVersion v2 = ContentVersion.create(10L, 2L, "v2");
        ReflectionTestUtils.setField(v2, "id", 2L);
        return new Fixture(itemRepository, typeRepository,
                new ViolationReconciliationService(itemRepository, typeRepository, penaltyService),
                content, v1, v2);
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
        return new ViolationEvidence(reason, 1.0, List.of());
    }

    private record Fixture(
            ViolationItemRepository itemRepository,
            ViolationTypeRepository typeRepository,
            ViolationReconciliationService service,
            Content content,
            ContentVersion v1,
            ContentVersion v2) {
    }
}
