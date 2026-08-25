package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.inspection.dto.ReinspectStaleResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;

class StaleContentInspectionServiceTest {

    private InspectionPolicyService policies;
    private GenerationService generations;
    private ContentVersionRepository versions;
    private ContentInspectionExecutionService inspectionService;
    private InspectionPolicy youtube;
    private InspectionPolicy instagram;
    private Generation generation;
    private StaleContentInspectionService service;

    @BeforeEach
    void setUp() {
        policies = mock(InspectionPolicyService.class);
        generations = mock(GenerationService.class);
        versions = mock(ContentVersionRepository.class);
        inspectionService = mock(ContentInspectionExecutionService.class);
        youtube = mock(InspectionPolicy.class);
        instagram = mock(InspectionPolicy.class);
        generation = mock(Generation.class);
        service = new StaleContentInspectionService(
                policies, generations, versions, inspectionService);
    }

    @Test
    void inspectsStaleLatestVersionsAndContinuesAfterFailure() {
        givenThreeStaleLatestVersions();
        doThrow(new RuntimeException("gemini")).when(inspectionService).inspect(12L);

        ReinspectStaleResponse response = service.reinspectStale(10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        InOrder repositoryOrder = inOrder(versions);
        repositoryOrder.verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.YOUTUBE), eq(8L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        repositoryOrder.verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.INSTAGRAM), eq(9L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        assertThat(pageable.getAllValues())
                .extracting(Pageable::getPageSize)
                .containsExactly(10, 8);
        InOrder inspectionOrder = inOrder(inspectionService);
        inspectionOrder.verify(inspectionService).inspect(11L);
        inspectionOrder.verify(inspectionService).inspect(12L);
        inspectionOrder.verify(inspectionService).inspect(13L);
        assertThat(response.targetCount()).isEqualTo(3);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.failedVersionIds()).containsExactly(12L);
    }

    @Test
    void collectsNonLeaseBusinessFailureAndContinuesWithProgress() {
        givenThreeStaleLatestVersions();
        List<Long> inspectedVersionIds = new ArrayList<>();
        List<ReinspectStaleResponse> snapshots = new ArrayList<>();

        ReinspectStaleResponse response = service.reinspectStale(
                2,
                Set.of(),
                versionId -> {
                    inspectedVersionIds.add(versionId);
                    if (versionId.equals(11L)) {
                        throw new BusinessException(ErrorCode.CONTENT_VERSION_NOT_FOUND);
                    }
                },
                snapshots::add);

        assertThat(inspectedVersionIds).containsExactly(11L, 12L);
        assertThat(snapshots).extracting(
                        ReinspectStaleResponse::successCount,
                        ReinspectStaleResponse::failureCount)
                .containsExactly(tuple(0, 0), tuple(0, 1), tuple(1, 1));
        assertThat(snapshots)
                .extracting(snapshot -> snapshot.successCount() + snapshot.failureCount())
                .containsExactly(0, 1, 2);
        assertThat(response.failedVersionIds()).containsExactly(11L);
    }

    @Test
    void reportsInitialAndPerItemImmutableSnapshots() {
        givenThreeStaleLatestVersions();
        List<ReinspectStaleResponse> snapshots = new ArrayList<>();

        ReinspectStaleResponse response = service.reinspectStale(
                10,
                Set.of(),
                versionId -> {
                    if (versionId.equals(12L)) {
                        throw new RuntimeException("검수 실패");
                    }
                },
                snapshots::add);

        assertThat(snapshots).extracting(
                        ReinspectStaleResponse::targetCount,
                        ReinspectStaleResponse::successCount,
                        ReinspectStaleResponse::failureCount)
                .containsExactly(
                        tuple(3, 0, 0),
                        tuple(3, 1, 0),
                        tuple(3, 1, 1),
                        tuple(3, 2, 1));
        assertThat(snapshots)
                .extracting(snapshot -> snapshot.successCount() + snapshot.failureCount())
                .containsExactly(0, 1, 2, 3);
        assertThat(snapshots)
                .extracting(ReinspectStaleResponse::failedVersionIds)
                .containsExactly(List.of(), List.of(), List.of(12L), List.of(12L));
        assertThatThrownBy(() -> snapshots.get(2).failedVersionIds().add(99L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(response).isEqualTo(snapshots.get(3));
    }

    @Test
    void excludesCopiedVersionIdsAndFillsTheLimitAcrossPlatforms() {
        givenThreeStaleLatestVersions();
        Set<Long> excludedVersionIds = new HashSet<>(Set.of(11L));
        when(versions.findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.YOUTUBE), eq(8L),
                eq(ContentVersionStatus.INSPECTING), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    excludedVersionIds.clear();
                    return List.of(11L, 12L);
                });
        List<Long> inspectedVersionIds = new ArrayList<>();

        ReinspectStaleResponse response = service.reinspectStale(
                2, excludedVersionIds, inspectedVersionIds::add, ignored -> {
                });

        assertThat(inspectedVersionIds).containsExactly(12L, 13L);
        assertThat(response.targetCount()).isEqualTo(2);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        InOrder repositoryOrder = inOrder(versions);
        repositoryOrder.verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.YOUTUBE), eq(8L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        repositoryOrder.verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.INSTAGRAM), eq(9L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        assertThat(pageable.getAllValues())
                .extracting(Pageable::getPageSize)
                .containsExactly(3, 2);
    }

    @Test
    void reportsNoStaleLatestVersionsWhenEveryCandidateIsExcluded() {
        givenThreeStaleLatestVersions();

        boolean hasStaleLatestVersions = service.hasStaleLatestVersions(Set.of(11L, 12L, 13L));

        assertThat(hasStaleLatestVersions).isFalse();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.YOUTUBE), eq(8L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.INSTAGRAM), eq(9L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        assertThat(pageable.getAllValues())
                .extracting(Pageable::getPageSize)
                .containsExactly(4, 4);
    }

    @Test
    void reportsAStaleLatestVersionAndStopsSelectingAfterTheFirstCandidate() {
        givenThreeStaleLatestVersions();

        boolean hasStaleLatestVersions = service.hasStaleLatestVersions(Set.of(11L));

        assertThat(hasStaleLatestVersions).isTrue();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(versions).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.YOUTUBE), eq(8L),
                eq(ContentVersionStatus.INSPECTING), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        verify(versions, never()).findStaleLatestVersionIds(
                eq(2L), eq(SnsPlatform.INSTAGRAM), eq(9L),
                eq(ContentVersionStatus.INSPECTING), any(Pageable.class));
    }

    @Test
    void stopsBeforeTheNextInspectionWhenProgressCallbackFails() {
        givenThreeStaleLatestVersions();
        List<Long> inspectedVersionIds = new ArrayList<>();
        IllegalStateException callbackFailure = new IllegalStateException("callback 실패");

        assertThatThrownBy(() -> service.reinspectStale(
                10,
                Set.of(),
                inspectedVersionIds::add,
                snapshot -> {
                    if (snapshot.successCount() == 1) {
                        throw callbackFailure;
                    }
                }))
                .isSameAs(callbackFailure);
        assertThat(inspectedVersionIds).containsExactly(11L);
    }

    @Test
    void rethrowsLeaseLossBeforeTheNextInspection() {
        givenThreeStaleLatestVersions();
        List<Long> inspectedVersionIds = new ArrayList<>();
        List<ReinspectStaleResponse> snapshots = new ArrayList<>();
        BusinessException leaseLost = new BusinessException(ErrorCode.TASK_RUN_LEASE_LOST);

        assertThatThrownBy(() -> service.reinspectStale(
                10,
                Set.of(),
                versionId -> {
                    inspectedVersionIds.add(versionId);
                    if (versionId.equals(11L)) {
                        throw leaseLost;
                    }
                },
                snapshots::add))
                .isSameAs(leaseLost);
        assertThat(inspectedVersionIds).containsExactly(11L);
        assertThat(snapshots)
                .extracting(snapshot -> snapshot.successCount() + snapshot.failureCount())
                .containsExactly(0);
    }

    @Test
    void rethrowsQuotaExhaustionBeforeTheNextInspectionAndProgressSnapshot() {
        givenThreeStaleLatestVersions();
        List<Long> inspectedVersionIds = new ArrayList<>();
        List<ReinspectStaleResponse> snapshots = new ArrayList<>();
        BusinessException quotaExceeded =
                new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED);

        assertThatThrownBy(() -> service.reinspectStale(
                10,
                Set.of(),
                versionId -> {
                    inspectedVersionIds.add(versionId);
                    if (versionId.equals(11L)) {
                        throw quotaExceeded;
                    }
                },
                snapshots::add))
                .isSameAs(quotaExceeded);
        assertThat(inspectedVersionIds).containsExactly(11L);
        assertThat(snapshots)
                .extracting(snapshot -> snapshot.successCount() + snapshot.failureCount())
                .containsExactly(0);
    }

    @Test
    void rejectsNullArgumentsBeforeSelectingTargets() {
        Consumer<Long> inspector = ignored -> {
        };
        Consumer<ReinspectStaleResponse> callback = ignored -> {
        };

        assertThatThrownBy(() -> service.reinspectStale(10, null, inspector, callback))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("배제 버전 ID 목록은 필수입니다.");
        assertThatThrownBy(() -> service.reinspectStale(10, Set.of(), null, callback))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("검수 실행 함수는 필수입니다.");
        assertThatThrownBy(() -> service.reinspectStale(10, Set.of(), inspector, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("진행 callback은 필수입니다.");
        assertThatThrownBy(() -> service.hasStaleLatestVersions(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("배제 버전 ID 목록은 필수입니다.");
        verifyNoInteractions(policies, generations, versions, inspectionService);
    }

    private void givenThreeStaleLatestVersions() {
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
    }
}
