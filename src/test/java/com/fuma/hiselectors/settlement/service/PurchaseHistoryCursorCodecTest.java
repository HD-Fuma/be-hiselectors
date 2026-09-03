package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PurchaseHistoryCursorCodecTest {

    private final PurchaseHistoryCursorCodec codec = new PurchaseHistoryCursorCodec();

    @Test
    void roundTripsCompositeCursor() {
        LocalDateTime purchasedAt = LocalDateTime.of(2026, 8, 31, 23, 59, 58, 123_456_000);

        String encoded = codec.encode(purchasedAt, 300L);
        PurchaseHistoryCursor decoded = codec.decode(encoded);

        assertThat(decoded.purchasedAt()).isEqualTo(purchasedAt);
        assertThat(decoded.purchaseHistoryId()).isEqualTo(300L);
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
