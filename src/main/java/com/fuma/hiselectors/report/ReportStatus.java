package com.fuma.hiselectors.report;

/**
 * 리포트 처리 상태. 기존 데이터가 쓰던 값(IN_PROGRESS/COMPLETED/FAILED)에
 * AI 분석 직후 상태를 더한다.
 *
 * <p>흐름: {@link #IN_PROGRESS} → {@link #AI_COMPLETED}(AI가 끝냄, 관리자 검수 대기)
 * → {@link #COMPLETED}(관리자 검수 완료). 실패는 {@link #FAILED}.
 */
public enum ReportStatus {
    /** 분석 진행 중. */
    IN_PROGRESS,
    /** AI 분석 완료. 관리자 검수 대기 — 자동 분석의 저장 기본값. */
    AI_COMPLETED,
    /** 관리자 검수까지 끝남. */
    COMPLETED,
    /** 분석 실패. */
    FAILED
}
