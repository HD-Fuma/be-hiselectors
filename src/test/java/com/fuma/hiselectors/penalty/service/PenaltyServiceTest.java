package com.fuma.hiselectors.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import com.fuma.hiselectors.penalty.dto.PenaltyCreateRequest;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PenaltyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 0, 0);

    private final SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
    private final SelectorsGenerationRepository membershipRepository =
            mock(SelectorsGenerationRepository.class);
    private final PenaltyHistoryRepository penaltyRepository = mock(PenaltyHistoryRepository.class);
    private final ViolationTypeRepository violationTypeRepository = mock(ViolationTypeRepository.class);
    private final ViolationItemRepository violationItemRepository =
            mock(ViolationItemRepository.class);
    private final PenaltyService service = new PenaltyService(
            selectorsRepository, membershipRepository, penaltyRepository, violationTypeRepository,
            violationItemRepository,
            Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
    private Selectors selectors;

    @BeforeEach
    void setUp() {
        selectors = Selectors.builder().userId(7L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE).build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        when(violationTypeRepository.existsById(4L)).thenReturn(true);
        when(selectorsRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(selectors));
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(
                new SelectorsGenerationResponse(
                        2L, "2기", NOW.minusMonths(1), NOW.minusDays(20),
                        NOW.minusDays(10), NOW.plusDays(10), "ACTIVE", NOW.minusDays(10))));
        when(penaltyRepository.saveAndFlush(any(PenaltyHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void thirdAccumulatedPenaltyImmediatelyBlacklistsAcrossGenerations() {
        when(penaltyRepository.countBySelectorsId(9L)).thenReturn(3L);

        var response = service.create(9L, new PenaltyCreateRequest(4L));

        assertThat(response.generationId()).isEqualTo(2L);
        assertThat(selectors.isBlacklisted()).isTrue();
        verify(penaltyRepository).countBySelectorsId(9L);
    }

    @Test
    void releasingPenaltyDoesNotResetAccumulatedCountOrChangeBlacklist() {
        PenaltyHistory active = PenaltyHistory.activate(9L, 2L, 4L, NOW.minusDays(1));
        when(violationItemRepository.existsOpenBySelectorsId(any(), any())).thenReturn(false);
        when(penaltyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                9L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThat(service.releaseIfEligible(9L)).isTrue();
        assertThat(active.getStatus()).isEqualTo(PenaltyStatus.RELEASED);
        assertThat(selectors.isBlacklisted()).isFalse();
        verify(penaltyRepository, never()).countBySelectorsId(any());
    }

    @Test
    void noCurrentActivityRejectsPenaltyBeforeSave() {
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(9L, new PenaltyCreateRequest(4L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(penaltyRepository, never()).saveAndFlush(any());
        verify(penaltyRepository, never()).countBySelectorsId(any());
    }

    @Test
    void unknownViolationTypeIsRejectedBeforeInsert() {
        when(violationTypeRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(9L, new PenaltyCreateRequest(99L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(penaltyRepository, never()).saveAndFlush(any());
    }

    @Test
    void automaticConfirmationDoesNotStartSecondActivePenaltyCycle() {
        PenaltyHistory active = PenaltyHistory.activate(9L, 2L, 4L, NOW.minusDays(1));
        when(penaltyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                9L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThat(service.activateIfAbsent(9L, 4L)).isFalse();

        verify(penaltyRepository, never()).saveAndFlush(any());
    }

    @Test
    void releasesActivePenaltyAfterAllViolationsAreClosed() {
        PenaltyHistory active = PenaltyHistory.activate(9L, 2L, 4L, NOW.minusDays(1));
        when(violationItemRepository.existsOpenBySelectorsId(any(), any())).thenReturn(false);
        when(penaltyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                9L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThat(service.releaseIfEligible(9L)).isTrue();
        assertThat(active.getStatus()).isEqualTo(PenaltyStatus.RELEASED);
    }

    @Test
    void administratorCannotCreateOverlappingActivePenaltyCycle() {
        PenaltyHistory active = PenaltyHistory.activate(9L, 2L, 4L, NOW.minusDays(1));
        when(penaltyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                9L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.create(9L, new PenaltyCreateRequest(4L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_PENALTY_ALREADY_EXISTS);
    }
}
