package com.fuma.hiselectors.proposal.repository;

import com.fuma.hiselectors.proposal.dto.ProposalHistoryResponse;
import com.fuma.hiselectors.proposal.model.ProposalHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProposalHistoryRepository extends JpaRepository<ProposalHistory, Long> {

    /** 관리자 제안 목록. creator_pool·admin 을 엔티티 조인해 최신순으로 돌려준다. */
    @Query(value = """
            select new com.fuma.hiselectors.proposal.dto.ProposalHistoryResponse(
                p.id, c.id, c.creatorName, c.snsCode, c.accountId, c.email, a.name, p.createdAt)
            from ProposalHistory p
            join com.fuma.hiselectors.creator.model.CreatorPool c on c.id = p.creatorId
            join com.fuma.hiselectors.admin.model.Admin a on a.id = p.adminId
            order by p.id desc
            """,
            countQuery = """
                    select count(p)
                    from ProposalHistory p
                    join com.fuma.hiselectors.creator.model.CreatorPool c on c.id = p.creatorId
                    join com.fuma.hiselectors.admin.model.Admin a on a.id = p.adminId
                    """)
    Page<ProposalHistoryResponse> findAllWithCreatorAndAdmin(Pageable pageable);
}
