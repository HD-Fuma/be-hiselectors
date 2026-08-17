package com.fuma.hiselectors.report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 지원자(application) 맥락에서 요청한 분석 결과. application_id 필수. */
@Entity
@Table(name = "application_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class ApplicationReport extends ReportBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_report_id")
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;
}
