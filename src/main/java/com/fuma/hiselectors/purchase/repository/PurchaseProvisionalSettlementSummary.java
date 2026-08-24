package com.fuma.hiselectors.purchase.repository;

import java.math.BigDecimal;

public interface PurchaseProvisionalSettlementSummary {

    BigDecimal getTotalSales();

    Long getPurchaseCount();
}
