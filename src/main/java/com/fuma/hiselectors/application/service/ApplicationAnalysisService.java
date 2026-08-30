package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.notification.service.NotificationRecorder;
import com.fuma.hiselectors.stt.ContentAddRequest;
import com.fuma.hiselectors.stt.CreatorEvaluationService;
import com.fuma.hiselectors.stt.InstagramSttClient;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private static final int MAX_YOUTUBE_SHORTS = 3;
    // Shorts 가 없는 롱폼-only 크리에이터의 신호 공백 방지용 fallback 개수.
    // 롱폼은 길어서 비쌈 → YoutubeSttClient 에서 앞부분만(endOffset) 잘라 분석해 비용을 묶는다.
    private static final int MAX_YOUTUBE_LONGFORM_FALLBACK = 2;
    private static final String QUANT_START = "APP_QUANT_START";
    private static final String QUAL_START = "APP_QUAL_START";
    private static final String QUAL_DONE = "APP_QUAL_DONE";

    private final ApplicationMediaRepository mediaRepository;
    private final ApplicationRepository applicationRepository;
    private final CreatorEvaluationService evaluationService;
    private final InstagramSttClient instagramSttClient;
    private final NotificationRecorder notificationRecorder;
    private final TransactionTemplate transactionTemplate;

    /** 미디어 전부 분석·적재 → 취합·리포트 저장 → 분석 상태 DONE. */
    public void analyzeAndReport(Long applicationId) {
        List<ApplicationMedia> media =
                mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(applicationId);
        if (media.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CONTENT_TO_EVALUATE);
        }

        // 헬스 게이트: 인스타 콘텐츠가 있는데 STT 워커가 죽어있으면 아예 시작하지 않는다.
        // 처리 도중 워커 장애로 인스타만 빠진 '부분 리포트'가 DONE 으로 저장되는 걸 막고,
        // transient 실패로 처리돼 재시도 예산도 소진하지 않는다(워커 복구 시 자동 재개).
        boolean hasInstagram = media.stream().anyMatch(m -> m.getSnsCode() != SnsPlatform.YOUTUBE);
        if (hasInstagram && !instagramSttClient.isHealthy()) {
            throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
        }

        notifyAnalysisFlow(QUANT_START, applicationId, "정량 분석을 시작했습니다.");

        // 비용이 큰 YouTube 영상 분석은 Shorts 중 조회수 상위 3건만 수행한다.
        Set<String> youtubeTargets = topYoutubeVideoIds(media);

        // 미디어별 STT/OCR 적재(외부호출, 멱등). 플랫폼별 취득 경로가 다르다.
        // 콘텐츠 1건 실패(전사 MAX_TOKENS, 만료 URL 등)는 지원자 전체를 막지 않도록 per-item 으로 잡고 skip.
        for (ApplicationMedia m : media) {
            try {
                if (m.getSnsCode() == SnsPlatform.YOUTUBE) {
                    // 유튜브는 media_url 이 없다. videoId(=sns_content_id)로 URL 전사.
                    if (!youtubeTargets.remove(m.getSnsContentId())) {
                        continue;
                    }
                    evaluationService.addYoutubeContent(applicationId, m.getSnsContentId());
                } else {
                    // 인스타는 media_url(CDN) 필요. 없는 건 취득 불가라 skip.
                    if (m.getMediaUrl() == null || m.getMediaUrl().isBlank()) {
                        continue;
                    }
                    // contentKey 는 미디어 단위 유니크 키(snsMediaId). 게시물ID(snsContentId)를 쓰면
                    // 캐러셀의 여러 미디어가 같은 키가 돼 uq_aca_content_key 충돌.
                    evaluationService.addContent(applicationId,
                            new ContentAddRequest(m.getSnsMediaId(), m.getMediaUrl(), m.getThumbnailUrl()));
                }
            } catch (RuntimeException e) {
                log.warn("콘텐츠 1건 분석 skip: applicationId={}, snsContentId={}, reason={}",
                        applicationId, m.getSnsContentId(), e.getMessage());
            }
        }

        // 취합 리포트 생성(Gemini) — 트랜잭션 밖.
        notifyAnalysisFlow(QUAL_START, applicationId, "정성평가를 시작했습니다.");
        ApplicationReport report = evaluationService.buildReport(applicationId);

        // 리포트 저장 + 콘텐츠 파기 + 분석완료(DONE)를 한 트랜잭션으로.
        // 중간에 죽어도 셋이 함께 커밋/롤백되어 "리포트는 있는데 상태는 PENDING" 불일치가 없다.
        transactionTemplate.executeWithoutResult(s -> {
            evaluationService.persistReport(applicationId, report);
            applicationRepository.findById(applicationId)
                    .ifPresent(a -> a.completeAnalysis(LocalDateTime.now()));
        });
        notifyAnalysisFlow(QUAL_DONE, applicationId, "정성평가가 완료되었습니다.");
    }

    private void notifyAnalysisFlow(String purposeCode, Long applicationId, String message) {
        try {
            notificationRecorder.recordInAppOnce(
                    purposeCode, applicationId, "지원자 #" + applicationId + " " + message);
        } catch (RuntimeException e) {
            log.warn("지원자 분석 알림 기록 실패: applicationId={}, purposeCode={}",
                    applicationId, purposeCode, e);
        }
    }

    private Set<String> topYoutubeVideoIds(List<ApplicationMedia> media) {
        // 비용이 큰 YouTube 영상 분석은 Shorts 중 조회수 상위만.
        Set<String> shorts = topYoutubeByType(media, ContentType.SHORTS, MAX_YOUTUBE_SHORTS);
        if (!shorts.isEmpty()) {
            return shorts;
        }
        // Shorts 가 하나도 없는 롱폼-only 지원자만: YouTube 신호 공백 방지로 롱폼 상위 몇 건 fallback.
        return topYoutubeByType(media, ContentType.LONG_FORM, MAX_YOUTUBE_LONGFORM_FALLBACK);
    }

    private Set<String> topYoutubeByType(List<ApplicationMedia> media, ContentType type, int limit) {
        Set<String> seen = new HashSet<>();
        Set<String> selected = new HashSet<>();
        media.stream()
                .filter(m -> m.getSnsCode() == SnsPlatform.YOUTUBE)
                .filter(m -> m.getContentType() == type)
                .filter(m -> m.getSnsContentId() != null && !m.getSnsContentId().isBlank())
                .filter(m -> seen.add(m.getSnsContentId()))
                .sorted(Comparator.comparing(
                                ApplicationMedia::getViewCount,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparingInt(ApplicationMedia::getSequenceNo))
                .limit(limit)
                .map(ApplicationMedia::getSnsContentId)
                .forEach(selected::add);
        return selected;
    }

    /**
     * 실패 기록. countRetry=false 면 재시도 카운트를 올리지 않는다(일시적 인프라 장애용).
     */
    public void markFailed(Long applicationId, String error, boolean countRetry) {
        transactionTemplate.executeWithoutResult(s ->
                applicationRepository.findById(applicationId)
                        .ifPresent(a -> a.failAnalysis(error, countRetry)));
    }
}
