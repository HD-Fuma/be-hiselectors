package com.fuma.hiselectors.proposal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크리에이터 제안 이력. 1행 = 관리자가 크리에이터에게 제안 메일 1회 발송.
 *
 * <p>기존 테이블이라 컬럼(제안 일시/크리에이터/관리자)만 있고 본문은 저장하지 않는다.
 * {@code created_at} 만 있고 {@code updated_at} 이 없으므로 BaseTimeEntity 를 상속하지 않는다.
 */
@Entity
@Table(name = "proposal_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proposal_history_id")
    private Long id;

    /** creator_pool.creator_pool_id. */
    @Column(name = "creator_id")
    private Long creatorId;

    /** admin.admin_id. 발송한 관리자. */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private ProposalHistory(Long creatorId, Long adminId) {
        this.creatorId = creatorId;
        this.adminId = adminId;
        this.createdAt = LocalDateTime.now();
    }

    public static ProposalHistory of(Long creatorId, Long adminId) {
        return new ProposalHistory(creatorId, adminId);
    }
}
