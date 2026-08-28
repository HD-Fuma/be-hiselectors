package com.fuma.hiselectors.content.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_report", uniqueConstraints = @UniqueConstraint(
        name = "uq_content_report_version_policy",
        columnNames = {"content_version_id", "inspection_policy_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport extends BaseTimeEntity {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

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

    @Column(name = "report_schema_version", length = 20)
    private String reportSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis", columnDefinition = "json")
    private ContentReportAnalysis analysis;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "execution_metadata", columnDefinition = "json")
    private Map<String, Object> executionMetadata = new LinkedHashMap<>();

    public static ContentReport create(Long contentVersionId, ContentReportData data,
                                       Long inspectionPolicyId) {
        return create(contentVersionId, ContentReportAnalysis.fromLegacy(data),
                inspectionPolicyId, Map.of());
    }

    public static ContentReport create(
            Long contentVersionId,
            ContentReportAnalysis analysis,
            Long inspectionPolicyId,
            Map<String, Object> executionMetadata) {
        ContentReport report = new ContentReport();
        report.contentVersionId = contentVersionId;
        report.inspectionPolicyId = inspectionPolicyId;
        report.replaceAnalysis(analysis, executionMetadata);
        return report;
    }

    public void replaceAnalysis(ContentReportAnalysis analysis,
                                Map<String, Object> executionMetadata) {
        this.reportSchemaVersion = CURRENT_SCHEMA_VERSION;
        this.analysis = analysis == null ? ContentReportAnalysis.empty() : analysis;
        this.executionMetadata = executionMetadata == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(executionMetadata);
        ContentReportAnalysis.Overview overview = this.analysis.overview();
        this.summary = overview.summary();
        this.purpose = overview.purpose();
        this.flow = overview.flow();
        this.overallAssessment = overview.overallAssessment();
    }
}
