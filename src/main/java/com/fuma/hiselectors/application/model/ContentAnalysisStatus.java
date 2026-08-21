package com.fuma.hiselectors.application.model;

/** 지원자 콘텐츠 STT/OCR 분석·리포트 생성 상태. 미디어 수집(DONE) 이후 단계. */
public enum ContentAnalysisStatus {
    PENDING,
    IN_PROGRESS,   // 스케줄러가 원자적으로 선점(중복 처리 방지). 완료 시 DONE, 실패 시 FAILED.
    DONE,
    FAILED
}
