package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.inspection.dto.ReinspectStaleResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService.InspectionResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class StaleContentInspectionServiceTest {

    @Test
    void inspectsStaleLatestVersionsAndContinuesAfterFailure() {
        InspectionPolicyService policies = mock(InspectionPolicyService.class);
        GenerationService generations = mock(GenerationService.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        ContentInspectionExecutionService inspectionService =
                mock(ContentInspectionExecutionService.class);
        InspectionPolicy youtube = mock(InspectionPolicy.class);
        InspectionPolicy instagram = mock(InspectionPolicy.class);
        Generation generation = mock(Generation.class);
        when(generation.getId()).thenReturn(2L);
        when(generations.getActive()).thenReturn(generation);
        when(youtube.getId()).thenReturn(8L);
        when(youtube.getPlatform()).thenReturn(SnsPlatform.YOUTUBE);
        when(instagram.getId()).thenReturn(9L);
        when(instagram.getPlatform()).thenReturn(SnsPlatform.INSTAGRAM);
        when(policies.requireAllActive()).thenReturn(List.of(youtube, instagram));
        when(versions.findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.YOUTUBE), eq(8L),
                eq(ContentVersionStatus.INSPECTING), any(Pageable.class)))
                .thenReturn(List.of(11L, 12L));
        when(versions.findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.INSTAGRAM), eq(9L),
                eq(ContentVersionStatus.INSPECTING), any(Pageable.class)))
                .thenReturn(List.of(13L));
        when(inspectionService.inspect(11L)).thenReturn(new InspectionResult(11L, 1));
        doThrow(new RuntimeException("gemini")).when(inspectionService).inspect(12L);
        when(inspectionService.inspect(13L)).thenReturn(new InspectionResult(13L, 0));
        StaleContentInspectionService service = new StaleContentInspectionService(
                policies, generations, versions, inspectionService);

        ReinspectStaleResponse response = service.reinspectStale(10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.YOUTUBE), eq(8L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        verify(inspectionService).inspect(11L);
        verify(inspectionService).inspect(12L);
        verify(inspectionService).inspect(13L);
        assertThat(response.targetCount()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.failedVersionIds()).containsExactly(12L);
    }
}
