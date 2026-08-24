package com.fuma.hiselectors.selectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.SelectorAccessLevel;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SelectorAccessServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 20, 0, 0);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
    private final SelectorsGenerationRepository membershipRepository =
            mock(SelectorsGenerationRepository.class);
    private final SelectorAccessService service = new SelectorAccessService(
            userRepository, selectorsRepository, membershipRepository,
            Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
    private Selectors selectors;

    @BeforeEach
    void setUp() {
        User user = User.builder().hiId("hi-user").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        selectors = Selectors.builder().userId(7L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE).build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        when(userRepository.findByHiId("hi-user")).thenReturn(Optional.of(user));
        when(selectorsRepository.findByUserId(7L)).thenReturn(Optional.of(selectors));
        when(selectorsRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(selectors));
        ReflectionTestUtils.setField(service, "lifecycleEnabled", true);
    }

    @Test
    void endsCurrentActiveSelectorActivity() {
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(generation(
                NOW.minusDays(1), NOW.plusMonths(2), NOW.minusMinutes(1))));

        service.endActivity("hi-user");

        assertThat(selectors.getSelectorsRoleId()).isEqualTo(Selectors.INACTIVE_ROLE);
        verify(selectorsRepository).findByUserIdForUpdate(7L);
    }

    @Test
    void endingInactiveSelectorActivityAgainIsIdempotent() {
        ReflectionTestUtils.setField(selectors, "selectorsRoleId", Selectors.INACTIVE_ROLE);

        service.endActivity("hi-user");

        assertThat(selectors.getSelectorsRoleId()).isEqualTo(Selectors.INACTIVE_ROLE);
        verify(membershipRepository, never()).findGenerationsOf(9L);
    }

    @Test
    void endingBlacklistedSelectorActivityPreservesBlacklist() {
        ReflectionTestUtils.setField(selectors, "selectorsRoleId", Selectors.BLACKLIST_ROLE);

        service.endActivity("hi-user");

        assertThat(selectors.getSelectorsRoleId()).isEqualTo(Selectors.BLACKLIST_ROLE);
        verify(membershipRepository, never()).findGenerationsOf(9L);
    }

    @Test
    void endingActivityRejectsMissingUser() {
        when(userRepository.findByHiId("missing-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.endActivity("missing-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void endingActivityRejectsMissingSelector() {
        when(selectorsRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.endActivity("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELECTOR_NOT_FOUND);
    }

    @Test
    void endingActivityRejectsDeletedSelector() {
        selectors.softDelete();

        assertThatThrownBy(() -> service.endActivity("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELECTOR_NOT_FOUND);
    }

    @Test
    void endingActivityRejectsActiveSelectorWithoutCurrentAccess() {
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.endActivity("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThat(selectors.getSelectorsRoleId()).isEqualTo(Selectors.ACTIVE_ROLE);
    }

    @Test
    void approvalTimeStartsCurrentAccessEvenBeforeActivityStart() {
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(generation(
                NOW.plusDays(10), NOW.plusMonths(2), NOW.minusMinutes(1))));

        assertThat(service.getAccess("hi-user").accessLevel())
                .isEqualTo(SelectorAccessLevel.CURRENT);
    }

    @Test
    void recentlyEndedGenerationIsPreviousAndCannotUseCurrentOnlyFeatures() {
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(generation(
                NOW.minusMonths(2), NOW.minusDays(1), NOW.minusMonths(3))));

        assertThat(service.getAccess("hi-user").accessLevel())
                .isEqualTo(SelectorAccessLevel.PREVIOUS);
        assertThatThrownBy(() -> service.requireCurrent("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void inactiveGenerationEndedMoreThanOneYearAgoHasNoSelectorAccess() {
        ReflectionTestUtils.setField(selectors, "selectorsRoleId", Selectors.INACTIVE_ROLE);
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(generation(
                NOW.minusYears(2), NOW.minusYears(1).minusNanos(1), NOW.minusYears(2))));

        assertThat(service.getAccess("hi-user").accessLevel())
                .isEqualTo(SelectorAccessLevel.NONE);
        assertThatThrownBy(() -> service.requireReadable("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void blacklistCanReadSettlementHistoryButCannotUseSettlementGuard() {
        ReflectionTestUtils.setField(selectors, "selectorsRoleId", Selectors.BLACKLIST_ROLE);
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(generation(
                NOW.minusMonths(2), NOW.minusDays(1), NOW.minusMonths(3))));

        assertThat(service.getAccess("hi-user").accessLevel())
                .isEqualTo(SelectorAccessLevel.BLACKLIST);
        assertThat(service.requireSettlementHistoryReadable("hi-user")).isSameAs(selectors);
        assertThatThrownBy(() -> service.requireSettlementReadable("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> service.requireSettlementWritable("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> service.requireReadable("hi-user"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void inactiveRoleGetsPreviousAccessForSettlementButNotCurrentGuard() {
        ReflectionTestUtils.setField(selectors, "selectorsRoleId", Selectors.INACTIVE_ROLE);
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(generation(
                NOW.minusDays(1), NOW.plusMonths(2), NOW.minusMinutes(1))));

        assertThat(service.getAccess("hi-user").accessLevel())
                .isEqualTo(SelectorAccessLevel.PREVIOUS);
        assertThat(service.requireSettlementReadable("hi-user")).isSameAs(selectors);
        assertThat(service.requireSettlementHistoryReadable("hi-user")).isSameAs(selectors);
        assertThatThrownBy(() -> service.requireCurrent("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void inactiveRoleCanUseSettlementWritableWithLockedLookupOnly() {
        ReflectionTestUtils.setField(selectors, "selectorsRoleId", Selectors.INACTIVE_ROLE);

        assertThat(service.requireSettlementWritable("hi-user")).isSameAs(selectors);

        verify(selectorsRepository).findByUserIdForUpdate(7L);
        verify(selectorsRepository, never()).findByUserId(7L);
        verify(membershipRepository, never()).findGenerationsOf(9L);
    }

    @Test
    void deletedSelectorCannotUseEitherSettlementGuard() {
        selectors.softDelete();

        assertThatThrownBy(() -> service.requireSettlementReadable("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> service.requireSettlementHistoryReadable("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> service.requireSettlementWritable("hi-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void disabledLifecycleKeepsActiveSelectorCurrentUntilPeriodsAreCorrected() {
        ReflectionTestUtils.setField(service, "lifecycleEnabled", false);
        when(membershipRepository.findGenerationsOf(9L)).thenReturn(List.of(generation(
                NOW.minusYears(2), NOW.minusYears(1), null)));

        assertThat(service.getAccess("hi-user").accessLevel())
                .isEqualTo(SelectorAccessLevel.CURRENT);
        assertThat(service.requireCurrent("hi-user")).isSameAs(selectors);
    }

    private SelectorsGenerationResponse generation(
            LocalDateTime activityStart, LocalDateTime activityEnd, LocalDateTime joinedAt) {
        return new SelectorsGenerationResponse(
                2L, "2기", NOW.minusMonths(4), NOW.minusMonths(3),
                activityStart, activityEnd, "ACTIVE", joinedAt);
    }
}
