package com.fuma.hiselectors.purchase.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "purchase_history", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_order_product", columnNames = {"order_no", "product_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseHistory extends BaseTimeEntity {

    private static final Map<PurchaseStatus, Set<PurchaseStatus>> ALLOWED_TRANSITIONS = Map.of(
            PurchaseStatus.PURCHASED, EnumSet.of(
                    PurchaseStatus.PURCHASE_CONFIRMED, PurchaseStatus.CANCEL_REQUESTED),
            PurchaseStatus.CANCEL_REQUESTED, EnumSet.of(PurchaseStatus.CANCELED),
            PurchaseStatus.PURCHASE_CONFIRMED, EnumSet.of(PurchaseStatus.RETURN_REQUESTED),
            PurchaseStatus.RETURN_REQUESTED, EnumSet.of(PurchaseStatus.RETURNED)
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_history_id")
    private Long id;

    @Column(name = "order_no", nullable = false, length = 100)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "selector_id")
    private Long selectorId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "regular_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal regularUnitPrice;

    @Column(name = "sale_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal saleUnitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseStatus status;

    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Builder
    private PurchaseHistory(String orderNo, Long userId, Long selectorId, Long productId,
                            Integer quantity, BigDecimal regularUnitPrice, BigDecimal saleUnitPrice,
                            BigDecimal discountAmount, BigDecimal paidAmount,
                            LocalDateTime purchasedAt) {
        this.orderNo = orderNo;
        this.userId = userId;
        this.selectorId = selectorId;
        this.productId = productId;
        this.quantity = quantity;
        this.regularUnitPrice = regularUnitPrice;
        this.saleUnitPrice = saleUnitPrice;
        this.discountAmount = discountAmount;
        this.paidAmount = paidAmount;
        this.status = PurchaseStatus.PURCHASED;
        this.purchasedAt = purchasedAt;
    }

    public boolean canTransitionTo(PurchaseStatus nextStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(nextStatus);
    }

    public void transitionTo(PurchaseStatus nextStatus, LocalDateTime confirmedAt) {
        this.status = nextStatus;
        if (nextStatus == PurchaseStatus.PURCHASE_CONFIRMED) {
            this.confirmedAt = confirmedAt;
        }
    }

    public boolean hasSamePurchaseIdentity(Long userId, Long selectorId, Integer quantity) {
        return this.userId.equals(userId)
                && java.util.Objects.equals(this.selectorId, selectorId)
                && this.quantity.equals(quantity);
    }
}
