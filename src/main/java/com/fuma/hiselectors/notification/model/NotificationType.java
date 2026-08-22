package com.fuma.hiselectors.notification.model;

public enum NotificationType {
    ACTIVITY_GUIDE,
    CONTENT_EDIT_DONE,
    CONTENT_EDIT_REQUEST,
    DEAD_LINK_NOTICE,
    FIRST_PURCHASE,
    SELECTION_APPROVED,
    SELECTION_REJECTED,
    SETTLEMENT_MISSING;

    public String getPurposeCode() {
        return name();
    }
}
