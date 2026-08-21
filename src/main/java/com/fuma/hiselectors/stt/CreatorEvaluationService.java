package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 지원자 종합 평가(A안 + DB 백업). 콘텐츠별 분석(비쌈)은 content_key 로 멱등 저장 →
 * 크래시·실패 후 재개 시 done 항목은 워커 재호출 없이 skip. 평가(쌈)는 저장분을 합쳐 Gemini 1회.
 *
 * <p><b>커넥션 주의:</b> 워커·Gemini 같은 장시간 외부 호출은 트랜잭션 <em>밖</em>에서 한다.
 * 여기에 @Transactional 을 걸면 외부 호출 수십 초 동안 DB 커넥션을 붙잡아 풀이 고갈된다.
 * 각 repository 호출은 그 자체로 짧은 트랜잭션이라, 사이의 외부 호출은 커넥션을 잡지 않는다.
 */
@Service
@RequiredArgsConstructor
public class CreatorEvaluationService {

    private final InstagramSttClient instagramClient;
    private final GeminiEvalClient evalClient;
    private final ApplicationContentAnalysisRepository repository;

    /** 콘텐츠 1건 분석 후 적재. content_key 가 이미 있으면 재분석하지 않고 저장분을 돌려준다(멱등). */
    public InstagramAnalysisResult addContent(Long applicantId, ContentAddRequest req) {
        // 1) 짧은 조회. 이미 있으면 워커 호출도 저장도 없이 반환.
        Optional<ApplicationContentAnalysis> existing = repository.findByContentKey(req.contentKey());
        if (existing.isPresent()) {
            return existing.get().toResult();
        }

        // 2) 오래 걸리는 워커 호출 — 트랜잭션 밖(커넥션 미보유).
        InstagramAnalysisResult result = instagramClient.analyze(
                req.reelUrl(), req.mediaUrl(), req.thumbnailUrl());

        // 3) 짧은 저장. 동시요청이 먼저 같은 content_key 를 저장했으면 중복은 성공으로 간주(멱등).
        try {
            repository.save(ApplicationContentAnalysis.from(applicantId, req.contentKey(), result));
        } catch (DataIntegrityViolationException duplicate) {
            // 다른 요청이 선점 저장 — 재분석 결과는 버리고 성공 취급.
        }
        return result;
    }

    /** 지원자의 저장된 콘텐츠를 합쳐 Gemini 1회 평가. 평가 후 삭제(무저장 원칙). */
    public ApplicantEvaluation evaluate(Long applicantId) {
        // 1) 짧은 읽기.
        List<ApplicationContentAnalysis> rows = repository.findByApplicantId(applicantId);
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

        // 2) Gemini 호출 — 트랜잭션 밖(커넥션 미보유).
        ApplicantEvaluation evaluation = evalClient.evaluate(merged);

        // 3) 짧은 삭제.
        repository.deleteByApplicantId(applicantId);
        return evaluation;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
