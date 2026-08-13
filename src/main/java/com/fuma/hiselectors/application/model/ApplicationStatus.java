package com.fuma.hiselectors.application.model;

/** 지원서 검수 상태. 제출 시 PENDING(검수전)으로 시작한다. */
public enum ApplicationStatus {
    PENDING,   // 검수전
    APPROVED,  // 승인
    REJECTED   // 반려
}
