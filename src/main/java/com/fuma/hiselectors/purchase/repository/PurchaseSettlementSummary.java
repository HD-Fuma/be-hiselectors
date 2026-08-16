package com.fuma.hiselectors.purchase.repository;

import java.math.BigDecimal;

public interface PurchaseSettlementSummary {

    BigDecimal getTotalSales();

    Long getConfirmedPurchaseCount();
}
