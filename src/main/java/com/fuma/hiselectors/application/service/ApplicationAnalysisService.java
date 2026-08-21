package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.stt.ContentAddRequest;
import com.fuma.hiselectors.stt.CreatorEvaluationService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 한 지원자의 수집된 미디어(application_media)를 전부 STT/OCR 분석해 application_content_analysis 에
 * 적재하고, 취합해 application_report 를 만든다. 미디어별 addContent 는 content_key(=media id) 멱등이라
 * 재시도 시 done 항목은 skip.
 *
 * <p>워커·Gemini 등 장시간 외부호출은 트랜잭션 밖. 상태 갱신만 TransactionTemplate 로 짧게.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationAnalysisService {

    private final ApplicationMediaRepository mediaRepository;
    private final ApplicationRepository applicationRepository;
    private final CreatorEvaluationService evaluationService;
    private final TransactionTemplate transactionTemplate;

    /** 미디어 전부 분석·적재 → 취합·리포트 저장 → 분석 상태 DONE. */
    public void analyzeAndReport(Long applicationId) {
        List<ApplicationMedia> media =
                mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(applicationId);
        if (media.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CONTENT_TO_EVALUATE);
        }

        // 미디어별 STT/OCR 적재(외부호출, 멱등). media_url 없는 건 취득 불가라 skip.
        for (ApplicationMedia m : media) {
            if (m.getMediaUrl() == null || m.getMediaUrl().isBlank()) {
                continue;
            }
            evaluationService.addContent(applicationId,
                    new ContentAddRequest(m.getSnsContentId(), m.getMediaUrl(), null));
        }

        // 취합 → application_report 저장 + 콘텐츠 파기(외부 Gemini 포함).
        evaluationService.evaluate(applicationId);

        transactionTemplate.executeWithoutResult(s ->
                applicationRepository.findById(applicationId)
                        .ifPresent(a -> a.completeAnalysis(LocalDateTime.now())));
    }

    /** 실패 기록(재시도 카운트 증가). */
    public void markFailed(Long applicationId, String error) {
        transactionTemplate.executeWithoutResult(s ->
                applicationRepository.findById(applicationId)
                        .ifPresent(a -> a.failAnalysis(error)));
    }
}
