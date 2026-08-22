package com.fuma.hiselectors.notification.model;

public enum NotificationType {
    ACTIVITY_GUIDE,
    CONTENT_EDIT_DONE,
    CONTENT_EDIT_REQUEST,
    DEAD_LINK_NOTICE,
    FIRST_PURCHASE,
    FIRST_REVENUE,
    SALES_100K,
    SALES_500K,
    SALES_1M,
    SALES_5M,
    SALES_10M,
    SELECTION_APPROVED,
    SELECTION_REJECTED,
    SETTLEMENT_MISSING;

    public String getPurposeCode() {
        return name();
    }
}
