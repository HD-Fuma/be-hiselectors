package com.fuma.hiselectors.report;

/** 분석 요청 맥락. 어느 report 테이블에 저장할지를 가른다. */
public enum ReportContext {
    /** 지원자 검수 → application_report (application_id). */
    APPLICATION,
    /** 콘텐츠 검수 → content_report (content_version_id). */
    CONTENT
}
