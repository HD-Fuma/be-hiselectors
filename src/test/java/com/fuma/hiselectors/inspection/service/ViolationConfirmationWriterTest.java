package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentRepository;
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
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PenaltyService penaltyService = mock(PenaltyService.class);

        ContentVersion originalVersion = ContentVersion.create(20L, 1L, "original");
        ReflectionTestUtils.setField(originalVersion, "id", 100L);
        ViolationItem item = ViolationItem.pending(
                originalVersion, 4L, evidence("최초 탐지 사유"));
        ReflectionTestUtils.setField(item, "id", 10L);
        ContentVersion latestVersion = ContentVersion.create(20L, 1L, "latest");
        ReflectionTestUtils.setField(latestVersion, "id", 101L);
        item.detectAgain(latestVersion, evidence("최신 탐지 사유"));

        Content content = mock(Content.class);
        when(content.getSelectorsId()).thenReturn(9L);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(9L);
        when(selectors.getUserId()).thenReturn(7L);
        when(selectors.getSelectorsNickname()).thenReturn("셀렉터");
        when(violationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(item));
        when(contentRepository.findById(20L)).thenReturn(Optional.of(content));
        when(selectorsRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(selectors));
        when(penaltyService.activateIfAbsent(
                9L, 101L, 4L, "최신 탐지 사유", "admin"))
                .thenReturn(true);

        ViolationConfirmationWriter writer = new ViolationConfirmationWriter(
                violationRepository, contentRepository, selectorsRepository, penaltyService);

        var result = writer.prepare(10L, "admin");

        assertThat(item.getStatus()).isEqualTo(ViolationStatus.VIOLATION_CONFIRMED);
        assertThat(result.reason()).isEqualTo("최신 탐지 사유");
        assertThat(result.penaltyCreated()).isTrue();
        verify(penaltyService).activateIfAbsent(
                9L, 101L, 4L, "최신 탐지 사유", "admin");
    }

    private ViolationEvidence evidence(String reason) {
        return new ViolationEvidence(reason, 1.0, List.of(), EvidenceSource.AI);
    }
}
