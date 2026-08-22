package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.dto.ApplicationStatusUpdateRequest;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationApprovalServiceTest {

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
    private final SelectorsGenerationRepository membershipRepository =
            mock(SelectorsGenerationRepository.class);
    private final SelectorsSnsAccountRepository snsAccountRepository =
            mock(SelectorsSnsAccountRepository.class);
    private final com.fuma.hiselectors.notification.service.NotificationService notificationService =
            mock(com.fuma.hiselectors.notification.service.NotificationService.class);
    private final ApplicationApprovalService service = new ApplicationApprovalService(
            applicationRepository, userRepository, selectorsRepository, membershipRepository,
            snsAccountRepository, notificationService);

    private Application application;
    private User user;

    @BeforeEach
    void setUp() {
        application = Application.builder()
                .userId(7L)
                .generationId(2L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsAccountId("UC-approved")
                .followerCount(12_345L)
                .status(ApplicationStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(application, "id", 31L);
        user = User.builder().name("스무자를넘어가는셀렉터스닉네임확인용이름").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(applicationRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(application));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void approvalCreatesNamedSelectorAndGenerationMembership() {
        when(selectorsRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());
        when(selectorsRepository.saveAndFlush(any(Selectors.class))).thenAnswer(invocation -> {
            Selectors selectors = invocation.getArgument(0);
            ReflectionTestUtils.setField(selectors, "id", 9L);
            return selectors;
        });

        var response = service.updateStatus(
                31L, new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED), "admin");

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
        ArgumentCaptor<Selectors> selectorsCaptor = ArgumentCaptor.forClass(Selectors.class);
        verify(selectorsRepository).saveAndFlush(selectorsCaptor.capture());
        assertThat(selectorsCaptor.getValue().getSelectorsCode()).isEqualTo("SEL-31");
        assertThat(selectorsCaptor.getValue().getSelectorsNickname()).hasSize(20);
        ArgumentCaptor<SelectorsGeneration> membershipCaptor =
                ArgumentCaptor.forClass(SelectorsGeneration.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getSelectorsId()).isEqualTo(9L);
        assertThat(membershipCaptor.getValue().getGenerationId()).isEqualTo(2L);
        ArgumentCaptor<SelectorsSnsAccount> accountCaptor =
                ArgumentCaptor.forClass(SelectorsSnsAccount.class);
        verify(snsAccountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getSelectorsId()).isEqualTo(9L);
        assertThat(accountCaptor.getValue().getSnsCode()).isEqualTo(SnsPlatform.YOUTUBE);
        assertThat(accountCaptor.getValue().getAccountId()).isEqualTo("UC-approved");
        assertThat(accountCaptor.getValue().getFollowerCount()).isEqualTo(12_345L);
    }

    @Test
    void approvalSynchronizesExistingSelectorAccountWithoutCreatingDuplicate() {
        Selectors selectors = Selectors.builder()
                .userId(7L)
                .selectorsRoleId(Selectors.INACTIVE_ROLE)
                .build();
        ReflectionTestUtils.setField(selectors, "id", 9L);
        SelectorsSnsAccount account = SelectorsSnsAccount.builder()
                .selectorsId(9L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("old-account")
                .followerCount(10L)
                .deleted(true)
                .build();
        when(selectorsRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(selectors));
        when(snsAccountRepository.findBySelectorsId(9L)).thenReturn(Optional.of(account));

        service.updateStatus(
                31L, new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED), "admin");

        assertThat(account.getSnsCode()).isEqualTo(SnsPlatform.YOUTUBE);
        assertThat(account.getAccountId()).isEqualTo("UC-approved");
        assertThat(account.getFollowerCount()).isEqualTo(12_345L);
        assertThat(account.isDeleted()).isFalse();
        verify(snsAccountRepository, never()).save(any());
    }

    @Test
    void approvalRejectsBlacklistedSelector() {
        Selectors selectors = Selectors.builder()
                .userId(7L)
                .selectorsRoleId(Selectors.BLACKLIST_ROLE)
                .build();
        when(selectorsRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(selectors));

        assertThatThrownBy(() -> service.updateStatus(
                31L, new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED), "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BLACKLISTED_SELECTOR);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void repeatedApprovalDoesNotReactivateSelector() {
        application.changeStatus(ApplicationStatus.APPROVED);

        var response = service.updateStatus(
                31L, new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED), "admin");

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
        verify(userRepository, never()).findByIdForUpdate(7L);
        verify(selectorsRepository, never()).findByUserIdForUpdate(7L);
        verify(membershipRepository, never()).save(any());
        verify(snsAccountRepository, never()).findBySelectorsId(any());
        verify(snsAccountRepository, never()).save(any());
    }

    @Test
    void selectorUniqueConflictBecomesDomainConflict() {
        when(selectorsRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());
        when(selectorsRepository.saveAndFlush(any(Selectors.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate user"));

        assertThatThrownBy(() -> service.updateStatus(
                31L, new ApplicationStatusUpdateRequest(ApplicationStatus.APPROVED), "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELECTOR_ALREADY_EXISTS);
    }
}
