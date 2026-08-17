package com.fuma.hiselectors.settlement.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "settlement_history", uniqueConstraints = @UniqueConstraint(
        name = "uk_settlement_selectors_month",
        columnNames = {"selectors_id", "settlement_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementHistory extends BaseTimeEntity {

    private static final Map<SettlementStatus, Set<SettlementStatus>> ALLOWED_TRANSITIONS = Map.of(
            SettlementStatus.CALCULATING, EnumSet.of(SettlementStatus.PAYMENT_PENDING),
            SettlementStatus.PAYMENT_PENDING, EnumSet.of(
                    SettlementStatus.PAYMENT_HOLD, SettlementStatus.SETTLED),
            SettlementStatus.PAYMENT_HOLD, EnumSet.of(SettlementStatus.PAYMENT_PENDING),
            SettlementStatus.SETTLED, Set.of()
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(name = "settlement_month", nullable = false)
    private LocalDateTime settlementMonth;

    @Column(name = "total_sales", nullable = false)
    private Long totalSales;

    @Column(nullable = false)
    private Long commission;

    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(name = "confirmed_purchase_count")
    private Long confirmedPurchaseCount;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    public static SettlementHistory create(Long selectorsId, LocalDateTime settlementMonth) {
        SettlementHistory history = new SettlementHistory();
        history.selectorsId = selectorsId;
        history.settlementMonth = settlementMonth;
        history.status = SettlementStatus.CALCULATING;
        history.totalSales = 0L;
        history.commission = 0L;
        history.confirmedPurchaseCount = 0L;
        return history;
    }

    public boolean isCalculating() {
        return status == SettlementStatus.CALCULATING;
    }

    /** 관리자 정합성 보정에서만 지급 대기 이력을 다시 계산 가능 상태로 되돌린다. */
    public void reopenPaymentPendingForRecalculation() {
        if (status != SettlementStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATUS_TRANSITION);
        }
        this.status = SettlementStatus.CALCULATING;
    }

    public void updateCalculation(long totalSales,
                                  long confirmedPurchaseCount,
                                  BigDecimal commissionRate,
                                  long commission,
                                  SettlementSourceCode sourceCode,
                                  LocalDateTime calculatedAt) {
        if (!isCalculating()) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATUS_TRANSITION);
        }
        this.totalSales = totalSales;
        this.confirmedPurchaseCount = confirmedPurchaseCount;
        this.commissionRate = commissionRate;
        this.commission = commission;
        this.calculatedAt = calculatedAt;
    }

    public void transitionTo(SettlementStatus nextStatus, LocalDateTime transitionAt) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(nextStatus)) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_STATUS_TRANSITION);
        }
        this.status = nextStatus;
        if (nextStatus == SettlementStatus.SETTLED) {
            this.settledAt = transitionAt;
        }
    }
}
