package com.fuma.hiselectors.selectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class SelectorsLifecycleServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 0, 0);

    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final SelectorsGenerationRepository membershipRepository =
            mock(SelectorsGenerationRepository.class);
    private final SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
    private final PurchaseHistoryRepository purchaseRepository =
            mock(PurchaseHistoryRepository.class);
    private final PenaltyHistoryRepository penaltyRepository = mock(PenaltyHistoryRepository.class);
    private final SelectorsLifecycleService service = new SelectorsLifecycleService(
            generationRepository, membershipRepository, selectorsRepository, purchaseRepository,
            penaltyRepository,
            Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
    private Generation ended;
    private Generation next;
    private Selectors selectors;

    @BeforeEach
    void setUp() {
        ended = generation(1L, NOW.minusMonths(2), NOW.minusDays(1));
        next = generation(2L, NOW.plusDays(10), NOW.plusMonths(2));
        selectors = Selectors.builder().selectorsRoleId(Selectors.ACTIVE_ROLE).build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        when(generationRepository.findAllByActivityEndDateLessThanOrderByActivityEndDateAsc(NOW))
                .thenReturn(List.of(ended));
        when(generationRepository
                .findFirstByActivityStartDateGreaterThanOrderByActivityStartDateAscIdAsc(
                        ended.getActivityEndDate()))
                .thenReturn(Optional.of(next));
        when(membershipRepository.findAllByGenerationId(1L)).thenReturn(List.of(
                SelectorsGeneration.builder().selectorsId(9L).generationId(1L).build()));
        when(selectorsRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(selectors));
    }

    @Test
    void exactThresholdEnrollsOnceAfterLockingSelector() {
        when(membershipRepository.existsBySelectorsIdAndGenerationId(9L, 2L)).thenReturn(false);
        when(purchaseRepository.sumConfirmedSalesByConfirmedAt(
                9L, PurchaseStatus.PURCHASE_CONFIRMED,
                ended.getActivityStartDate(), ended.getActivityEndDate().plusNanos(1_000)))
                .thenReturn(new BigDecimal("500000"));

        assertThat(service.enrollQualifiedSelectors()).isEqualTo(1);

        InOrder order = inOrder(selectorsRepository, membershipRepository);
        order.verify(selectorsRepository).findByIdForUpdate(9L);
        order.verify(membershipRepository).existsBySelectorsIdAndGenerationId(9L, 2L);
        verify(membershipRepository).save(org.mockito.ArgumentMatchers.argThat(membership ->
                membership.getSelectorsId().equals(9L)
                        && membership.getGenerationId().equals(2L)));
    }

    @Test
    void belowThresholdDeactivatesWhenNoOtherCurrentMembershipExists() {
        when(membershipRepository.existsBySelectorsIdAndGenerationId(9L, 2L)).thenReturn(false);
        when(purchaseRepository.sumConfirmedSalesByConfirmedAt(
                9L, PurchaseStatus.PURCHASE_CONFIRMED,
                ended.getActivityStartDate(), ended.getActivityEndDate().plusNanos(1_000)))
                .thenReturn(new BigDecimal("499999"));
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of());

        assertThat(service.enrollQualifiedSelectors()).isZero();

        assertThat(selectors.getSelectorsRoleId()).isEqualTo(Selectors.INACTIVE_ROLE);
        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void existingNextMembershipMakesRetryIdempotentAndKeepsCurrentActive() {
        when(membershipRepository.existsBySelectorsIdAndGenerationId(9L, 2L)).thenReturn(true);
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(
                new SelectorsGenerationResponse(
                        2L, "2기", NOW, NOW.plusDays(1),
                        NOW.plusDays(10), NOW.plusMonths(2), "ACTIVE", NOW.minusMinutes(1))));

        assertThat(service.enrollQualifiedSelectors()).isZero();

        assertThat(selectors.getSelectorsRoleId()).isEqualTo(Selectors.ACTIVE_ROLE);
        verify(purchaseRepository, never()).sumConfirmedSalesByConfirmedAt(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotCreateMembershipForAnAlreadyEndedNextGeneration() {
        next = generation(2L, NOW.minusDays(10), NOW.minusDays(2));
        when(generationRepository
                .findFirstByActivityStartDateGreaterThanOrderByActivityStartDateAscIdAsc(
                        ended.getActivityEndDate()))
                .thenReturn(Optional.of(next));
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of());

        assertThat(service.enrollQualifiedSelectors()).isZero();

        verify(purchaseRepository, never()).sumConfirmedSalesByConfirmedAt(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(membershipRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void releasesSubThresholdGenerationPenaltyWithoutDeletingHistoryOnEveryRetry() {
        PenaltyHistory penalty = PenaltyHistory.activate(
                9L, 1L, 4L, NOW.minusDays(10));
        when(generationRepository
                .findFirstByActivityStartDateGreaterThanOrderByActivityStartDateAscIdAsc(
                        ended.getActivityEndDate()))
                .thenReturn(Optional.empty());
        when(penaltyRepository.findAllBySelectorsIdAndGenerationIdAndStatus(
                9L, 1L, PenaltyStatus.ACTIVE)).thenReturn(List.of(penalty));
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of());

        service.enrollQualifiedSelectors();
        service.enrollQualifiedSelectors();

        assertThat(penalty.getStatus()).isEqualTo(PenaltyStatus.RELEASED);
        assertThat(penalty.getEndedAt()).isEqualTo(NOW);
        verify(penaltyRepository, times(2))
                .findAllBySelectorsIdAndGenerationIdAndStatus(9L, 1L, PenaltyStatus.ACTIVE);
        verify(penaltyRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private Generation generation(Long id, LocalDateTime activityStart, LocalDateTime activityEnd) {
        Generation generation = Generation.builder()
                .generationName(id + "기")
                .startDate(activityStart.minusMonths(1))
                .endDate(activityStart.minusDays(1))
                .activityStartDate(activityStart)
                .activityEndDate(activityEnd)
                .status(GenerationStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(generation, "id", id);
        return generation;
    }
}
