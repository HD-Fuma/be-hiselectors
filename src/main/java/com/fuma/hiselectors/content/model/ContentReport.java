package com.fuma.hiselectors.content.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_report_id")
    private Long id;

    @Column(name = "content_version_id", nullable = false)
    private Long contentVersionId;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "purpose", length = 100)
    private String purpose;

    @Column(name = "flow", columnDefinition = "text")
    private String flow;

    @Column(name = "overall_assessment", columnDefinition = "text")
    private String overallAssessment;

    // 마이그레이션 이전 리포트는 null일 수 있으며 새 검수 결과부터 항상 채운다.
    @Column(name = "inspection_policy_id")
    private Long inspectionPolicyId;

    public static ContentReport create(Long contentVersionId, ContentReportData data,
                                       Long inspectionPolicyId) {
        ContentReport report = new ContentReport();
        report.contentVersionId = contentVersionId;
        report.summary = data.summary();
        report.purpose = data.purpose();
        report.flow = data.flow();
        report.overallAssessment = data.overallAssessment();
        report.inspectionPolicyId = inspectionPolicyId;
        return report;
    }
}
