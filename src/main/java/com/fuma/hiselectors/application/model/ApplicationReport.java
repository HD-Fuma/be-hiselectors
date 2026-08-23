package com.fuma.hiselectors.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 지원자(application) 맥락에서 요청한 분석 결과. application_id 필수. */
@Entity
@Table(name = "application_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_report_id")
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    /** 정량 점수. 산출 로직 생기기 전까지 null. */
    @Column(name = "quantity_score", precision = 5, scale = 2)
    private BigDecimal quantityScore;

    /** 정성 점수. 산출 로직 생기기 전까지 null. */
    @Column(name = "quality_score", precision = 5, scale = 2)
    private BigDecimal qualityScore;

    /** 요약. json 컬럼이라 유효 JSON 문자열로 저장한다(서비스에서 직렬화). */
    @Column(columnDefinition = "json")
    private String summary;

    /** 대표 카테고리 코드(BEAUTY 등). 로컬 엔진 zero-shot. */
    @Column(length = 20)
    private String category;

    /** 키워드 목록을 구분자로 이은 문자열. 로컬 엔진. */
    @Column(length = 500)
    private String keywords;

    /** 타겟층. 아직 미산출 → null. */
    @Column(length = 19)
    private String target;

    /** 콘텐츠 스타일(enum). LLM insight. */
    @Column(name = "content_style", length = 19)
    private String contentStyle;

    /** 톤앤매너. LLM insight. */
    @Column(length = 500)
    private String tone;

    /** 언급된 협업/협찬 브랜드. LLM insight. */
    @Column(name = "brand_history", length = 500)
    private String brandHistory;

    /** 강점. LLM insight. */
    @Column(length = 500)
    private String strength;

    /** 유의점 + 넓은 위험 + 욕설 확정을 합친 주의사항. LLM insight. */
    @Column(length = 500)
    private String warning;

    /** 대표 콘텐츠(정량=engagement 최고) 링크. 만료되는 media_url 대신 안정적인 퍼머링크/watch URL. */
    @Column(name = "representative_content_url", columnDefinition = "TEXT")
    private String representativeContentUrl;

    /** 대표 콘텐츠 타입(REELS/VIDEO 등). FE 렌더링용. */
    @Column(name = "representative_content_type", length = 20)
    private String representativeContentType;

    /** 대표 선정 근거(조회수). null 가능. */
    @Column(name = "representative_view_count")
    private Long representativeViewCount;

    /** 대표 콘텐츠 1건의 카테고리(전체 취합 category 와 별개, 콘텐츠별 값). */
    @Column(name = "representative_category", length = 20)
    private String representativeCategory;

    /** 대표 콘텐츠 1건의 키워드. */
    @Column(name = "representative_keywords", length = 500)
    private String representativeKeywords;

    /** 처리 상태. */
    @Column(length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
