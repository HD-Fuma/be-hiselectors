package com.fuma.hiselectors.report.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public abstract class ReportBase {

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

    /** 처리 상태. */
    @Column(length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
