package com.fuma.hiselectors.proposal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.dto.ProposalHistoryResponse;
import com.fuma.hiselectors.proposal.model.ProposalHistory;
import com.fuma.hiselectors.proposal.repository.ProposalHistoryRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProposalServiceTest {

    private final ProposalHistoryRepository proposalHistoryRepository =
            mock(ProposalHistoryRepository.class);
    private final CreatorPoolRepository creatorPoolRepository = mock(CreatorPoolRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final ProposalMailService proposalMailService = mock(ProposalMailService.class);
    private final ProposalService service = new ProposalService(
            proposalHistoryRepository, creatorPoolRepository, adminRepository, proposalMailService);

    private Admin admin(Long id) {
        Admin admin = Admin.builder().loginId("mgr").name("홍길동").role("ADMIN").build();
        ReflectionTestUtils.setField(admin, "id", id);
        return admin;
    }

    private CreatorPool creator(Long id, String email) {
        CreatorPool creator = CreatorPool.builder()
                .snsCode("YOUTUBE").accountId("UC-1").creatorName("도윤의 집밥").email(email).build();
        ReflectionTestUtils.setField(creator, "id", id);
        return creator;
    }

    @Test
    void 제안_발송_이력저장_후_메일발송_응답반환() {
        when(adminRepository.findByLoginId("mgr")).thenReturn(Optional.of(admin(3L)));
        when(creatorPoolRepository.findByIdAndDeletedFalse(5L))
                .thenReturn(Optional.of(creator(5L, "creator@example.com")));
        when(proposalHistoryRepository.save(any(ProposalHistory.class))).thenAnswer(inv -> {
            ProposalHistory saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });

        ProposalHistoryResponse response = service.propose(
                "mgr", new ProposalCreateRequest(5L, "맞춤 제목", "맞춤 본문"));

        assertThat(response.proposalHistoryId()).isEqualTo(99L);
        assertThat(response.creatorId()).isEqualTo(5L);
        assertThat(response.creatorName()).isEqualTo("도윤의 집밥");
        assertThat(response.email()).isEqualTo("creator@example.com");
        assertThat(response.adminName()).isEqualTo("홍길동");
        verify(proposalMailService).send(
                any(CreatorPool.class), any(Admin.class),
                org.mockito.ArgumentMatchers.eq("맞춤 제목"),
                org.mockito.ArgumentMatchers.eq("맞춤 본문"));
    }

    @Test
    void 제목과_본문을_생략하면_기본_템플릿으로_발송한다() {
        when(adminRepository.findByLoginId("mgr")).thenReturn(Optional.of(admin(3L)));
        when(creatorPoolRepository.findByIdAndDeletedFalse(5L))
                .thenReturn(Optional.of(creator(5L, "creator@example.com")));
        when(proposalHistoryRepository.save(any(ProposalHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.propose("mgr", new ProposalCreateRequest(5L));

        verify(proposalMailService).send(any(CreatorPool.class), any(Admin.class));
        verify(proposalMailService, never()).send(
                any(CreatorPool.class), any(Admin.class), any(String.class), any(String.class));
    }

    @Test
    void 이메일_없는_크리에이터면_발송하지_않고_예외() {
        when(adminRepository.findByLoginId("mgr")).thenReturn(Optional.of(admin(3L)));
        when(creatorPoolRepository.findByIdAndDeletedFalse(5L))
                .thenReturn(Optional.of(creator(5L, "  ")));

        assertThatThrownBy(() -> service.propose("mgr", new ProposalCreateRequest(5L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CREATOR_EMAIL_REQUIRED);

        verify(proposalHistoryRepository, never()).save(any());
        verify(proposalMailService, never()).send(any(), any());
    }

    @Test
    void 관리자_없으면_예외() {
        when(adminRepository.findByLoginId("mgr")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.propose("mgr", new ProposalCreateRequest(5L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADMIN_NOT_FOUND);
    }
}
