package com.fuma.hiselectors.selectors.excellence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceRewardType;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelection;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelectionType;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceQueryRepository;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceQueryRepository.SalesCandidate;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceSelectionRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class SelectorExcellenceGenerationWorkerTest {

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 8, 17, 0, 0);

    private final GenerationRepository generationRepository = mock(GenerationRepository.class);
    private final SelectorExcellenceQueryRepository queryRepository =
            mock(SelectorExcellenceQueryRepository.class);
    private final SelectorExcellenceSelectionRepository selectionRepository =
            mock(SelectorExcellenceSelectionRepository.class);
    private final SelectorExcellenceGenerationWorker worker =
            new SelectorExcellenceGenerationWorker(
                    generationRepository, queryRepository, selectionRepository);
    private Generation generation;

    @BeforeEach
    void setUp() {
        generation = generation(
                7L,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 8, 10, 23, 59, 59));
        when(generationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(generation));
    }

    @Test
    void selectsThresholdAchieversAndExactlyTopThreeWithRewardSnapshots() {
        when(queryRepository.findSalesCandidates(
                7L,
                generation.getActivityStartDate(),
                LocalDateTime.of(2026, 8, 11, 0, 0),
                AS_OF)).thenReturn(List.of(
                        candidate(40L, "9000000", 4),
                        candidate(30L, "10000000", 1),
                        candidate(10L, "20000000", 3),
                        candidate(20L, "10000000", 2)));

        SelectorExcellenceGenerationWorker.SelectionResult result =
                worker.select(7L, AS_OF, 7);

        assertThat(result.processed()).isTrue();
        assertThat(result.selectionCount()).isEqualTo(6);
        assertThat(generation.getSelectorExcellenceSelectedAt()).isEqualTo(AS_OF);

        ArgumentCaptor<Iterable<SelectorExcellenceSelection>> captor =
                ArgumentCaptor.forClass(Iterable.class);
        verify(selectionRepository).saveAll(captor.capture());
        List<SelectorExcellenceSelection> selections = new ArrayList<>();
        captor.getValue().forEach(selections::add);

        assertThat(selections)
                .filteredOn(selection -> selection.getSelectionType()
                        == SelectorExcellenceSelectionType.SALES_THRESHOLD)
                .hasSize(3)
                .allSatisfy(selection -> {
                    assertThat(selection.getRankNo()).isNull();
                    assertThat(selection.getRewardType())
                            .isEqualTo(SelectorExcellenceRewardType.DISCOUNT_COUPON);
                    assertThat(selection.getRewardValue()).isEqualTo(50L);
                    assertThat(selection.getRewardQuantity()).isEqualTo(2);
                    assertThat(selection.getSelectedAt()).isEqualTo(AS_OF);
                });
        assertThat(selections)
                .filteredOn(selection -> selection.getSelectionType()
                        == SelectorExcellenceSelectionType.SALES_RANKING)
                .extracting(
                        SelectorExcellenceSelection::getSelectorsId,
                        SelectorExcellenceSelection::getRankNo,
                        SelectorExcellenceSelection::getRewardValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, 1, 500_000L),
                        org.assertj.core.groups.Tuple.tuple(20L, 2, 300_000L),
                        org.assertj.core.groups.Tuple.tuple(30L, 3, 100_000L));
        assertThat(selections)
                .filteredOn(selection -> selection.getSelectorsId().equals(40L))
                .isEmpty();
    }

    @Test
    void marksGenerationCompleteEvenWhenThereAreNoConfirmedOrders() {
        when(queryRepository.findSalesCandidates(any(), any(), any(), any()))
                .thenReturn(List.of());

        SelectorExcellenceGenerationWorker.SelectionResult result =
                worker.select(7L, AS_OF, 7);

        assertThat(result.processed()).isTrue();
        assertThat(result.selectionCount()).isZero();
        assertThat(generation.getSelectorExcellenceSelectedAt()).isEqualTo(AS_OF);
        verify(selectionRepository).saveAll(List.of());
    }

    @Test
    void skipsAlreadyCompletedGenerationAfterTakingRowLock() {
        generation.markSelectorExcellenceSelected(AS_OF.minusDays(1));

        SelectorExcellenceGenerationWorker.SelectionResult result =
                worker.select(7L, AS_OF, 7);

        assertThat(result.processed()).isFalse();
        verify(queryRepository, never()).findSalesCandidates(any(), any(), any(), any());
        verify(selectionRepository, never()).saveAll(any());
    }

    @Test
    void retriesLaterWithoutCompletingWhenAutoConfirmationStillHasPendingPurchases() {
        when(queryRepository.hasPendingPurchases(
                7L,
                generation.getActivityStartDate(),
                LocalDateTime.of(2026, 8, 11, 0, 0)))
                .thenReturn(true);

        SelectorExcellenceGenerationWorker.SelectionResult result =
                worker.select(7L, AS_OF, 7);

        assertThat(result.processed()).isFalse();
        assertThat(generation.getSelectorExcellenceSelectedAt()).isNull();
        verify(queryRepository, never()).findSalesCandidates(any(), any(), any(), any());
        verify(selectionRepository, never()).saveAll(any());
    }

    @Test
    void respectsAnExplicitActivityEndTimeInsteadOfIncludingTheRestOfTheDay() {
        Generation middayEnd = generation(
                9L,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 8, 10, 12, 0));

        assertThat(SelectorExcellenceGenerationWorker.activityEndExclusive(middayEnd))
                .isEqualTo(LocalDateTime.of(2026, 8, 10, 12, 0, 0, 1_000));
    }

    @Test
    void graceZeroNeverSelectsBeforeTheActualActivityEndTime() {
        Generation endingLaterToday = generation(
                8L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 17, 18, 0));
        when(generationRepository.findByIdForUpdate(8L))
                .thenReturn(Optional.of(endingLaterToday));

        SelectorExcellenceGenerationWorker.SelectionResult result =
                worker.select(8L, AS_OF, 0);

        assertThat(result.processed()).isFalse();
        assertThat(endingLaterToday.getSelectorExcellenceSelectedAt()).isNull();
        verify(queryRepository, never()).findSalesCandidates(any(), any(), any(), any());
    }

    @Test
    void eachGenerationRunsInRequiresNewTransaction() throws NoSuchMethodException {
        Method method = SelectorExcellenceGenerationWorker.class.getMethod(
                "select", Long.class, LocalDateTime.class, int.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private Generation generation(
            Long id, LocalDateTime activityStart, LocalDateTime activityEnd) {
        Generation result = Generation.builder()
                .generationName(id + "기")
                .startDate(activityStart.minusMonths(1))
                .endDate(activityStart.minusDays(1))
                .activityStartDate(activityStart)
                .activityEndDate(activityEnd)
                .status(GenerationStatus.INACTIVE)
                .build();
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private SalesCandidate candidate(Long selectorsId, String sales, long orders) {
        return new SalesCandidate(selectorsId, new BigDecimal(sales), orders);
    }
}
