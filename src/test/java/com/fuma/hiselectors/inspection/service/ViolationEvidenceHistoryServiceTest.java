package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ViolationEvidenceHistoryServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-20T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void insertsSnapshotForNewPolicyVersion() {
        ViolationEvidenceHistoryRepository repository = mock(ViolationEvidenceHistoryRepository.class);
        when(repository.findByViolationItemIdAndContentVersionIdAndInspectionPolicyId(
                20L, 2L, 9L)).thenReturn(Optional.empty());
        ViolationEvidenceHistoryService service =
                new ViolationEvidenceHistoryService(repository, clock);

        service.upsert(item(), version(), 9L);

        ArgumentCaptor<ViolationEvidenceHistory> captor =
                ArgumentCaptor.forClass(ViolationEvidenceHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getViolationItemId()).isEqualTo(20L);
        assertThat(captor.getValue().getContentVersionId()).isEqualTo(2L);
        assertThat(captor.getValue().getInspectionPolicyId()).isEqualTo(9L);
    }

    @Test
    void overwritesSnapshotForSameVersionAndPolicy() {
        ViolationEvidenceHistoryRepository repository = mock(ViolationEvidenceHistoryRepository.class);
        ViolationEvidenceHistory existing = ViolationEvidenceHistory.create(
                20L, 2L, 9L,
                new ViolationEvidence("이전", 0.4, List.of(), EvidenceSource.AI),
                LocalDateTime.of(2026, 8, 19, 1, 0));
        when(repository.findByViolationItemIdAndContentVersionIdAndInspectionPolicyId(
                20L, 2L, 9L)).thenReturn(Optional.of(existing));
        ViolationEvidenceHistoryService service =
                new ViolationEvidenceHistoryService(repository, clock);

        service.upsert(item(), version(), 9L);

        verify(repository, never()).save(any());
        assertThat(existing.getEvidence().reason()).isEqualTo("최신");
        assertThat(existing.getDetectedAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 3, 0));
    }

    @Test
    void insertsAnotherSnapshotWhenPolicyChangesOnSameVersion() {
        ViolationEvidenceHistoryRepository repository = mock(ViolationEvidenceHistoryRepository.class);
        when(repository.findByViolationItemIdAndContentVersionIdAndInspectionPolicyId(
                20L, 2L, 10L)).thenReturn(Optional.empty());
        ViolationEvidenceHistoryService service =
                new ViolationEvidenceHistoryService(repository, clock);

        service.upsert(item(), version(), 10L);

        verify(repository).save(any());
    }

    private ViolationItem item() {
        ContentVersion version = ContentVersion.create(10L, 2L, "v2");
        ReflectionTestUtils.setField(version, "id", 2L);
        ViolationItem item = ViolationItem.pending(
                version, 100L,
                new ViolationEvidence("최신", 1.0, List.of(), EvidenceSource.AI));
        ReflectionTestUtils.setField(item, "id", 20L);
        return item;
    }

    private ContentVersion version() {
        ContentVersion version = ContentVersion.create(10L, 2L, "v2");
        ReflectionTestUtils.setField(version, "id", 2L);
        return version;
    }
}
