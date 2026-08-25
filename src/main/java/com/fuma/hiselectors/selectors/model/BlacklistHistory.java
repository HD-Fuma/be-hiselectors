package com.fuma.hiselectors.selectors.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blacklist_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlacklistHistory extends BaseTimeEntity {

    public static final String ACTIVE_STATUS = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "blacklist_history_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public static BlacklistHistory activate(Long selectorsId, String reason) {
        BlacklistHistory history = new BlacklistHistory();
        history.selectorsId = selectorsId;
        history.reason = reason;
        history.status = ACTIVE_STATUS;
        return history;
    }
}
