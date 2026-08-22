package com.fuma.hiselectors.notification.model;

public enum NotificationType {
    ACTIVITY_GUIDE,
    CONTENT_EDIT_DONE,
    CONTENT_EDIT_REQUEST,
    DEAD_LINK_NOTICE,
    FIRST_PURCHASE,
    FIRST_REVENUE,
    LAST_MONTH_SALES,
    MID_MONTH_ACTIVITY,
    SALES_100K,
    SALES_500K,
    SALES_1M,
    SALES_5M,
    SALES_10M,
    ORDERS_10,
    ORDERS_50,
    ORDERS_100,
    SELECTION_APPROVED,
    SELECTION_REJECTED,
    SETTLEMENT_MISSING,
    WEEKLY_SALES_GROWTH;

    public String getPurposeCode() {
        return name();
    }
}
