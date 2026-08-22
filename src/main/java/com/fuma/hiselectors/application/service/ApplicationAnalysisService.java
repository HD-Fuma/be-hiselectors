package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.model.SnsPlatform;
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

        // 미디어별 STT/OCR 적재(외부호출, 멱등). 플랫폼별 취득 경로가 다르다.
        for (ApplicationMedia m : media) {
            if (m.getSnsCode() == SnsPlatform.YOUTUBE) {
                // 유튜브는 media_url 이 없다. videoId(=sns_content_id)로 URL 전사.
                if (m.getSnsContentId() == null || m.getSnsContentId().isBlank()) {
                    continue;
                }
                evaluationService.addYoutubeContent(applicationId, m.getSnsContentId());
            } else {
                // 인스타는 media_url(CDN) 필요. 없는 건 취득 불가라 skip.
                if (m.getMediaUrl() == null || m.getMediaUrl().isBlank()) {
                    continue;
                }
                // thumbnailUrl: ApplicationMedia 에 컬럼 추가되면 null → m.getThumbnailUrl() 로 교체.
                evaluationService.addContent(applicationId,
                        new ContentAddRequest(m.getSnsContentId(), m.getMediaUrl(), null));
            }
        }

        // 취합 리포트 생성(Gemini) — 트랜잭션 밖.
        ApplicationReport report = evaluationService.buildReport(applicationId);

        // 리포트 저장 + 콘텐츠 파기 + 분석완료(DONE)를 한 트랜잭션으로.
        // 중간에 죽어도 셋이 함께 커밋/롤백되어 "리포트는 있는데 상태는 PENDING" 불일치가 없다.
        transactionTemplate.executeWithoutResult(s -> {
            evaluationService.persistReport(applicationId, report);
            applicationRepository.findById(applicationId)
                    .ifPresent(a -> a.completeAnalysis(LocalDateTime.now()));
        });
    }

    /** 실패 기록(재시도 카운트 증가). */
    public void markFailed(Long applicationId, String error) {
        transactionTemplate.executeWithoutResult(s ->
                applicationRepository.findById(applicationId)
                        .ifPresent(a -> a.failAnalysis(error)));
    }
}
