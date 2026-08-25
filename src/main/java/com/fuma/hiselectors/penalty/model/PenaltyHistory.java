package com.fuma.hiselectors.penalty.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "penalty_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PenaltyHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_history_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Column(name = "generation_id")
    private Long generationId;

    @Column(name = "content_version_id")
    private Long contentVersionId;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private PenaltySource source;

    @Column(name = "granted_by_admin_id")
    private Long grantedByAdminId;

    @Column(name = "released_by_admin_id")
    private Long releasedByAdminId;

    @Column(name = "violation_type_id", nullable = false)
    private Long violationTypeId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PenaltyStatus status;

    public static PenaltyHistory activate(Long selectorsId, Long violationTypeId,
                                          LocalDateTime startedAt) {
        return activate(selectorsId, null, null, violationTypeId, null,
                PenaltySource.AUTOMATIC, null, startedAt);
    }

    public static PenaltyHistory activate(Long selectorsId, Long generationId,
                                          Long violationTypeId, LocalDateTime startedAt) {
        return activate(selectorsId, generationId, null, violationTypeId, null,
                PenaltySource.AUTOMATIC, null, startedAt);
    }

    public static PenaltyHistory activate(Long selectorsId, Long generationId,
                                          Long contentVersionId, Long violationTypeId,
                                          String reason, PenaltySource source,
                                          Long grantedByAdminId, LocalDateTime startedAt) {
        PenaltyHistory history = new PenaltyHistory();
        history.selectorsId = selectorsId;
        history.generationId = generationId;
        history.contentVersionId = contentVersionId;
        history.reason = reason;
        history.source = source;
        history.grantedByAdminId = grantedByAdminId;
        history.violationTypeId = violationTypeId;
        history.startedAt = startedAt;
        history.status = PenaltyStatus.ACTIVE;
        return history;
    }

    public void release(LocalDateTime endedAt) {
        if (status == PenaltyStatus.ACTIVE) {
            status = PenaltyStatus.RELEASED;
            this.endedAt = endedAt;
        }
    }

    public void releaseByAdmin(Long adminId, LocalDateTime endedAt) {
        if (status == PenaltyStatus.ACTIVE) {
            release(endedAt);
            releasedByAdminId = adminId;
        }
    }
}
