package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PurchaseHistoryCursorCodec {

    private static final String VERSION = "v1";
    private static final String DELIMITER = "|";

    String encode(LocalDateTime purchasedAt, Long purchaseHistoryId) {
        Objects.requireNonNull(purchasedAt);
        Objects.requireNonNull(purchaseHistoryId);
        String value = String.join(
                DELIMITER, VERSION, purchasedAt.toString(), purchaseHistoryId.toString());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    PurchaseHistoryCursor decode(String encodedCursor) {
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(encodedCursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw invalidCursor();
            }
            LocalDateTime purchasedAt = LocalDateTime.parse(parts[1]);
            Long purchaseHistoryId = Long.valueOf(parts[2]);
            if (purchaseHistoryId <= 0) {
                throw invalidCursor();
            }
            return new PurchaseHistoryCursor(purchasedAt, purchaseHistoryId);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, exception);
        }
    }

    private BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 cursor입니다.");
    }
}
