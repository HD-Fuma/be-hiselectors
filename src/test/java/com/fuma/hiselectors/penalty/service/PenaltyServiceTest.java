package com.fuma.hiselectors.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import com.fuma.hiselectors.penalty.dto.PenaltyCreateRequest;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltySource;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.BlacklistHistory;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.BlacklistHistoryRepository;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class PenaltyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 0, 0);

    private final SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
    private final BlacklistHistoryRepository blacklistHistoryRepository =
            mock(BlacklistHistoryRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final SelectorsGenerationRepository membershipRepository =
            mock(SelectorsGenerationRepository.class);
    private final PenaltyHistoryRepository penaltyRepository = mock(PenaltyHistoryRepository.class);
    private final ViolationTypeRepository violationTypeRepository = mock(ViolationTypeRepository.class);
    private final ViolationItemRepository violationItemRepository =
            mock(ViolationItemRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PenaltyService service = new PenaltyService(
            selectorsRepository, blacklistHistoryRepository, adminRepository,
            membershipRepository, penaltyRepository, violationTypeRepository,
             violationItemRepository,
             Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
             eventPublisher);
    private Selectors selectors;
    private Admin admin;

    @BeforeEach
    void setUp() {
        selectors = Selectors.builder().userId(7L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE).build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        admin = Admin.builder().loginId("admin").name("관리자").build();
        ReflectionTestUtils.setField(admin, "id", 3L);
        when(adminRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
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

        var response = service.create(
                9L, new PenaltyCreateRequest(4L, "관리자 수동 사유"), "admin");

        assertThat(response.generationId()).isEqualTo(2L);
        assertThat(selectors.isBlacklisted()).isTrue();
        verify(penaltyRepository).countBySelectorsId(9L);
        ArgumentCaptor<BlacklistHistory> historyCaptor =
                ArgumentCaptor.forClass(BlacklistHistory.class);
        verify(blacklistHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getSelectorsId()).isEqualTo(9L);
        assertThat(historyCaptor.getValue().getReason())
                .isEqualTo("패널티 누적 3회로 인한 자동 블랙리스트 전환");
        assertThat(historyCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void manualPenaltyStoresReasonWithoutContentVersion() {
        when(penaltyRepository.countBySelectorsId(9L)).thenReturn(1L);

        var response = service.create(
                9L, new PenaltyCreateRequest(4L, "  관리자 수동 사유  "), "admin");

        ArgumentCaptor<PenaltyHistory> captor = ArgumentCaptor.forClass(PenaltyHistory.class);
        verify(penaltyRepository).saveAndFlush(captor.capture());
        PenaltyHistory saved = captor.getValue();
        assertThat(saved.getContentVersionId()).isNull();
        assertThat(saved.getReason()).isEqualTo("관리자 수동 사유");
        assertThat(saved.getSource()).isEqualTo(PenaltySource.MANUAL);
        assertThat(saved.getGrantedByAdminId()).isEqualTo(3L);
        assertThat(response.contentVersionId()).isNull();
        assertThat(response.reason()).isEqualTo("관리자 수동 사유");
    }

    @Test
    void automaticPenaltyStoresRelatedContentVersionAndReason() {
        when(penaltyRepository.countBySelectorsId(9L)).thenReturn(1L);

        assertThat(service.activateIfAbsent(
                9L, 100L, 4L, "자동 검수 사유", "admin")).isTrue();

        ArgumentCaptor<PenaltyHistory> captor = ArgumentCaptor.forClass(PenaltyHistory.class);
        verify(penaltyRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContentVersionId()).isEqualTo(100L);
        assertThat(captor.getValue().getReason()).isEqualTo("자동 검수 사유");
        assertThat(captor.getValue().getSource()).isEqualTo(PenaltySource.AUTOMATIC);
        assertThat(captor.getValue().getGrantedByAdminId()).isEqualTo(3L);
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

        assertThatThrownBy(() -> service.create(
                9L, new PenaltyCreateRequest(4L, "관리자 수동 사유"), "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(penaltyRepository, never()).saveAndFlush(any());
        verify(penaltyRepository, never()).countBySelectorsId(any());
    }

    @Test
    void unknownViolationTypeIsRejectedBeforeInsert() {
        when(violationTypeRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                9L, new PenaltyCreateRequest(99L, "관리자 수동 사유"), "admin"))
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

        assertThat(service.activateIfAbsent(
                9L, 100L, 4L, "자동 검수 사유", "admin")).isFalse();

        verify(penaltyRepository, never()).saveAndFlush(any());
    }

    @Test
    void releasesActivePenaltyAfterAllViolationsAreClosed() {
        PenaltyHistory active = PenaltyHistory.activate(
                9L, 2L, 100L, 4L, "자동 검수 사유",
                PenaltySource.AUTOMATIC, 3L, NOW.minusDays(1));
        ReflectionTestUtils.setField(active, "id", 11L);
        when(violationItemRepository.existsOpenBySelectorsId(any(), any())).thenReturn(false);
        when(penaltyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                9L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThat(service.releaseIfEligible(9L)).isTrue();
        assertThat(active.getStatus()).isEqualTo(PenaltyStatus.RELEASED);
        verify(eventPublisher).publishEvent(
                new PenaltyReleasedEvent(3L, 11L, 9L));
    }

    @Test
    void administratorCannotCreateOverlappingActivePenaltyCycle() {
        PenaltyHistory active = PenaltyHistory.activate(9L, 2L, 4L, NOW.minusDays(1));
        when(penaltyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                9L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.create(
                9L, new PenaltyCreateRequest(4L, "관리자 수동 사유"), "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVE_PENALTY_ALREADY_EXISTS);
    }

    @Test
    void manualPenaltyIsNotReleasedByViolationReconciliation() {
        PenaltyHistory manual = PenaltyHistory.activate(
                9L, 2L, null, 4L, "관리자 수동 사유",
                PenaltySource.MANUAL, 3L, NOW.minusDays(1));
        when(violationItemRepository.existsOpenBySelectorsId(any(), any())).thenReturn(false);
        when(penaltyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                9L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(manual));

        assertThat(service.releaseIfEligible(9L)).isFalse();
        assertThat(manual.getStatus()).isEqualTo(PenaltyStatus.ACTIVE);
    }

    @Test
    void administratorCanReleaseActivePenaltyAndIsRecorded() {
        PenaltyHistory manual = PenaltyHistory.activate(
                9L, 2L, null, 4L, "관리자 수동 사유",
                PenaltySource.MANUAL, 3L, NOW.minusDays(1));
        ReflectionTestUtils.setField(manual, "id", 11L);
        when(penaltyRepository.findByIdAndSelectorsIdForUpdate(11L, 9L))
                .thenReturn(Optional.of(manual));

        var response = service.releaseManually(9L, 11L, "admin");

        assertThat(manual.getStatus()).isEqualTo(PenaltyStatus.RELEASED);
        assertThat(manual.getEndedAt()).isEqualTo(NOW);
        assertThat(manual.getReleasedByAdminId()).isEqualTo(3L);
        assertThat(response.releasedByAdminId()).isEqualTo(3L);
        verify(eventPublisher).publishEvent(
                new PenaltyReleasedEvent(3L, 11L, 9L));
    }
}
