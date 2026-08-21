package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
import com.fuma.hiselectors.application.service.ReportStatus;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.stt.model.ApplicationContentAnalysis;
import com.fuma.hiselectors.stt.repository.ApplicationContentAnalysisRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Instagram 지원자 평가(B안: 유튜브와 분리). 콘텐츠별 분석(비쌈)은 content_key 로 멱등 저장 →
 * 크래시·실패 후 재개 시 done 항목은 skip. 취합은 저장된 콘텐츠들을 합쳐:
 * <ul>
 *   <li>category·keywords = 로컬 분석의 결정적 취합(최빈·병합)</li>
 *   <li>강점·스타일·톤·위험 등 insight = Gemini 1회(콘텐츠별로 안 태움 — 취득 자유로운 인스타 이점)</li>
 * </ul>
 * 결과를 application_report 에 저장한다.
 *
 * <p><b>커넥션 주의:</b> 워커·Gemini 장시간 외부 호출은 트랜잭션 밖. DB 쓰기만 TransactionTemplate 로 짧게.
 */
@Service
@RequiredArgsConstructor
public class CreatorEvaluationService {

    private static final int TEXT_MAX = 500;
    private static final int STYLE_MAX = 19;

    private final InstagramSttClient instagramClient;
    private final GeminiEvalClient evalClient;
    private final ApplicationContentAnalysisRepository contentRepository;
    private final ApplicationReportRepository reportRepository;
    private final TransactionTemplate transactionTemplate;

    /** 콘텐츠 1건 분석 후 적재. content_key 가 이미 있으면 재분석하지 않고 저장분을 돌려준다(멱등). */
    public InstagramAnalysisResult addContent(Long applicationId, ContentAddRequest req) {
        Optional<ApplicationContentAnalysis> existing =
                contentRepository.findByContentKey(req.contentKey());
        if (existing.isPresent()) {
            return existing.get().toResult();
        }

        // 오래 걸리는 워커 호출 — 트랜잭션 밖(커넥션 미보유).
        InstagramAnalysisResult result = instagramClient.analyze(
                req.reelUrl(), req.mediaUrl(), req.thumbnailUrl());

        try {
            contentRepository.save(
                    ApplicationContentAnalysis.from(applicationId, req.contentKey(), result));
        } catch (DataIntegrityViolationException duplicate) {
            // 동시요청 선점 저장 — 성공 취급(멱등).
        }
        return result;
    }

    /**
     * 지원자의 저장된 콘텐츠를 취합해 application_report 1건으로 저장한다.
     * category·keywords 는 결정적 취합, 나머지 insight 는 Gemini 1회. 지원자당 1건(재실행 시 교체).
     */
    public ApplicationReport evaluate(Long applicationId) {
        List<ApplicationContentAnalysis> rows = contentRepository.findByApplicantId(applicationId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CONTENT_TO_EVALUATE);
        }
        String merged = rows.stream()
                .map(c -> (safe(c.getStt()) + " " + safe(c.getOcr())).strip())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n"));
        if (merged.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "콘텐츠에 전사·자막 내용이 없습니다.");
        }

        // Gemini 취합 — 트랜잭션 밖.
        ContentInsight insight = evalClient.insight(merged);

        ApplicationReport report = ApplicationReport.builder()
                .applicationId(applicationId)
                .category(mode(rows, ApplicationContentAnalysis::getCategory))
                .keywords(clip(union(rows, ApplicationContentAnalysis::getKeywords), TEXT_MAX))
                .contentStyle(clip(insight.contentStyle(), STYLE_MAX))
                .tone(clip(insight.tone(), TEXT_MAX))
                .strength(clip(join(insight.strengths()), TEXT_MAX))
                .warning(clip(join(mergeWarnings(insight)), TEXT_MAX))
                .brandHistory(clip(join(insight.collabBrands()), TEXT_MAX))
                .status(ReportStatus.AI_COMPLETED.name())
                .build();

        // 쓰기만 짧은 트랜잭션(기존 지우고 새로 저장 = 재실행 멱등).
        return transactionTemplate.execute(status -> {
            reportRepository.deleteByApplicationId(applicationId);
            return reportRepository.save(report);
        });
    }

    /** 콘텐츠별 category 최빈값. 없으면 null. */
    private String mode(List<ApplicationContentAnalysis> rows,
                        java.util.function.Function<ApplicationContentAnalysis, String> getter) {
        return rows.stream()
                .map(getter)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** 콤마로 이어진 값들을 펼쳐 순서보존 중복제거 후 다시 잇는다. 없으면 null. */
    private String union(List<ApplicationContentAnalysis> rows,
                         java.util.function.Function<ApplicationContentAnalysis, String> getter) {
        Set<String> merged = new LinkedHashSet<>();
        for (ApplicationContentAnalysis row : rows) {
            String value = getter.apply(row);
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String part : value.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    merged.add(trimmed);
                }
            }
        }
        return merged.isEmpty() ? null : String.join(", ", merged);
    }

    /** 유의점 + 넓은 위험 + (욕설 확정 시 표식)을 합친다. */
    private List<String> mergeWarnings(ContentInsight insight) {
        List<String> merged = new ArrayList<>();
        if (insight.cautions() != null) {
            merged.addAll(insight.cautions());
        }
        if (insight.risks() != null) {
            merged.addAll(insight.risks());
        }
        if (insight.hateConfirmed()) {
            merged.add("욕설/혐오");
        }
        return merged;
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    private String clip(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
