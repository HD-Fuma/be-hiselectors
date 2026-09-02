package com.fuma.hiselectors.settlement.dto;

import java.util.List;

public record SettlementPurchaseHistoryCursorResponse(
        List<SettlementPurchaseHistoryResponse> content,
        String nextCursor,
        boolean hasNext) {

    public SettlementPurchaseHistoryCursorResponse {
        content = List.copyOf(content);
    }
}
