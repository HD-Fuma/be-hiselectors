package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지원자 콘텐츠 1건의 분석 결과(영속). content_key 로 멱등 — 이미 있으면 워커 재호출을 건너뛴다.
 * 크래시/실패 후 재개 시 done 항목은 skip. 지원자는 동의(policy_agreed_at) 하에 저장되며,
 * 평가 완료 후 지원자 단위로 삭제한다(보존기간 정책이 있으면 그때 삭제로 대체).
 */
@Entity
@Table(name = "application_content_analysis", uniqueConstraints =
        @UniqueConstraint(name = "uq_aca_content_key", columnNames = "content_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationContentAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_analysis_id")
    private Long id;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    /** 멱등 키. 릴스 shortcode 또는 Graph API media id 등 콘텐츠 고유값. */
    @Column(name = "content_key", nullable = false, length = 200)
    private String contentKey;

    @Column(name = "source", length = 20)
    private String source;

    @Lob
    @Column(name = "stt")
    private String stt;

    @Lob
    @Column(name = "ocr")
    private String ocr;

    @Column(name = "category", length = 30)
    private String category;

    /** 로컬 신호 키워드. 콤마 구분 저장. */
    @Lob
    @Column(name = "keywords")
    private String keywords;

    @Column(name = "hate_suspected", nullable = false)
    private boolean hateSuspected;

    @Builder
    private ApplicationContentAnalysis(Long applicantId, String contentKey, String source,
                                       String stt, String ocr, String category,
                                       String keywords, boolean hateSuspected) {
        this.applicantId = applicantId;
        this.contentKey = contentKey;
        this.source = source;
        this.stt = stt;
        this.ocr = ocr;
        this.category = category;
        this.keywords = keywords;
        this.hateSuspected = hateSuspected;
    }

    public static ApplicationContentAnalysis from(Long applicantId, String contentKey,
                                                  InstagramAnalysisResult r) {
        InstagramAnalysisResult.Analysis a = r.analysis();
        return ApplicationContentAnalysis.builder()
                .applicantId(applicantId)
                .contentKey(contentKey)
                .source(r.source())
                .stt(r.stt())
                .ocr(r.ocr())
                .category(a != null && a.category() != null ? a.category().label() : null)
                .keywords(a != null && a.keywords() != null ? String.join(",", a.keywords()) : null)
                .hateSuspected(a != null && a.hate() != null && Boolean.TRUE.equals(a.hate().suspected()))
                .build();
    }

    /** 저장분으로 콘텐츠 결과 재구성(멱등 skip 시 반환용). score 등 일부는 보존 안 함. */
    public InstagramAnalysisResult toResult() {
        List<String> kw = keywords == null || keywords.isBlank()
                ? List.of() : List.of(keywords.split(","));
        return new InstagramAnalysisResult(source, stt, ocr,
                new InstagramAnalysisResult.Analysis(
                        kw,
                        new InstagramAnalysisResult.Category(category, null, null),
                        new InstagramAnalysisResult.Hate(List.of(), Map.of(), hateSuspected)));
    }
}
