package com.fuma.hiselectors.content.model;

import com.fuma.hiselectors.common.BaseTimeEntity;
import com.fuma.hiselectors.inspection.model.ContentReportData;
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

    @Column(name = "content_version_id", nullable = false, unique = true)
    private Long contentVersionId;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "purpose", length = 100)
    private String purpose;

    @Column(name = "flow", columnDefinition = "text")
    private String flow;

    @Column(name = "overall_assessment", columnDefinition = "text")
    private String overallAssessment;

    public static ContentReport create(Long contentVersionId, ContentReportData data) {
        ContentReport report = new ContentReport();
        report.contentVersionId = contentVersionId;
        report.update(data);
        return report;
    }

    public void update(ContentReportData data) {
        this.summary = data.summary();
        this.purpose = data.purpose();
        this.flow = data.flow();
        this.overallAssessment = data.overallAssessment();
    }
}
