package com.fuma.hiselectors.settlement.service;

import java.time.LocalDateTime;

record PurchaseHistoryCursor(LocalDateTime purchasedAt, Long purchaseHistoryId) {
}
