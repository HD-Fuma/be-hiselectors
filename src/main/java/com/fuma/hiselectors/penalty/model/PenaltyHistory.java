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
        PenaltyHistory history = new PenaltyHistory();
        history.selectorsId = selectorsId;
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
}
