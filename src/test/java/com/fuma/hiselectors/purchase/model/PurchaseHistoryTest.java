package com.fuma.hiselectors.purchase.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PurchaseHistoryTest {

    @Test
    void supportsPurchaseConfirmationAndReturnFlow() {
        PurchaseHistory purchase = purchase();
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 8, 11, 12, 0);

        assertThat(purchase.canTransitionTo(PurchaseStatus.PURCHASE_CONFIRMED)).isTrue();
        purchase.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, confirmedAt);
        assertThat(purchase.getConfirmedAt()).isEqualTo(confirmedAt);

        assertThat(purchase.canTransitionTo(PurchaseStatus.RETURN_REQUESTED)).isTrue();
        purchase.transitionTo(PurchaseStatus.RETURN_REQUESTED, null);
        assertThat(purchase.canTransitionTo(PurchaseStatus.RETURNED)).isTrue();
        purchase.transitionTo(PurchaseStatus.RETURNED, null);

        assertThat(purchase.canTransitionTo(PurchaseStatus.CANCEL_REQUESTED)).isFalse();
        assertThat(purchase.canTransitionTo(PurchaseStatus.PURCHASE_CONFIRMED)).isFalse();
    }

    @Test
    void supportsCancellationOnlyBeforeConfirmation() {
        PurchaseHistory purchase = purchase();

        assertThat(purchase.canTransitionTo(PurchaseStatus.CANCEL_REQUESTED)).isTrue();
        purchase.transitionTo(PurchaseStatus.CANCEL_REQUESTED, null);
        assertThat(purchase.canTransitionTo(PurchaseStatus.CANCELED)).isTrue();
        purchase.transitionTo(PurchaseStatus.CANCELED, null);

        assertThat(purchase.canTransitionTo(PurchaseStatus.PURCHASE_CONFIRMED)).isFalse();
        assertThat(purchase.canTransitionTo(PurchaseStatus.RETURN_REQUESTED)).isFalse();
    }

    private PurchaseHistory purchase() {
        return PurchaseHistory.builder()
                .orderNo("ORDER-1")
                .userId(1L)
                .selectorsId(2L)
                .productId(3L)
                .quantity(2)
                .regularUnitPrice(new BigDecimal("10000"))
                .saleUnitPrice(new BigDecimal("9500"))
                .discountAmount(new BigDecimal("1000"))
                .paidAmount(new BigDecimal("19000"))
                .purchasedAt(LocalDateTime.of(2026, 8, 11, 10, 0))
                .build();
    }
}
