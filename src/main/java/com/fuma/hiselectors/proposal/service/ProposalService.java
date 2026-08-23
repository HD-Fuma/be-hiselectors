package com.fuma.hiselectors.proposal.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProposalService {

    private final ProposalHistoryRepository proposalHistoryRepository;
    private final CreatorPoolRepository creatorPoolRepository;
    private final AdminRepository adminRepository;
    private final ProposalMailService proposalMailService;

    @Transactional(readOnly = true)
    public Page<ProposalHistoryResponse> list(Pageable pageable) {
        return proposalHistoryRepository.findAllWithCreatorAndAdmin(pageable);
    }

    /**
     * 크리에이터에게 제안 메일을 보내고 이력을 남긴다.
     *
     * <p>이력을 먼저 저장해 링크에 쓸 ID를 확보한 뒤 발송한다. 발송이 실패하면
     * 예외로 트랜잭션을 롤백해 이력도 남지 않는다(둘 다 성공하거나 둘 다 실패).
     */
    @Transactional
    public ProposalHistoryResponse propose(String adminLoginId, ProposalCreateRequest request) {
        Admin admin = adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        CreatorPool creator = creatorPoolRepository.findByIdAndDeletedFalse(request.creatorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        if (creator.getEmail() == null || creator.getEmail().isBlank()) {
            throw new BusinessException(ErrorCode.CREATOR_EMAIL_REQUIRED);
        }

        ProposalHistory saved = proposalHistoryRepository.save(
                ProposalHistory.of(creator.getId(), admin.getId()));
        if (request.subject() == null) {
            proposalMailService.send(creator, admin);
        } else {
            proposalMailService.send(creator, admin, request.subject(), request.body());
        }

        return new ProposalHistoryResponse(
                saved.getId(),
                creator.getId(),
                creator.getCreatorName(),
                creator.getSnsCode(),
                creator.getAccountId(),
                creator.getEmail(),
                admin.getName(),
                saved.getCreatedAt());
    }
}
