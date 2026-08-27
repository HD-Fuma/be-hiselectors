package com.fuma.hiselectors.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.stt.ContentAddRequest;
import com.fuma.hiselectors.stt.CreatorEvaluationService;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.notification.service.NotificationRecorder;
import com.fuma.hiselectors.stt.InstagramSttClient;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ApplicationAnalysisServiceTest {

    private final ApplicationMediaRepository mediaRepository = mock(ApplicationMediaRepository.class);
    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final CreatorEvaluationService evaluationService = mock(CreatorEvaluationService.class);
    private final InstagramSttClient instagramSttClient = mock(InstagramSttClient.class);
    private final NotificationRecorder notificationRecorder = mock(NotificationRecorder.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final ApplicationAnalysisService service = new ApplicationAnalysisService(
            mediaRepository, applicationRepository, evaluationService, instagramSttClient,
            notificationRecorder, transactionTemplate);

    private ApplicationMedia media(
            SnsPlatform snsCode,
            String snsContentId,
            String snsMediaId,
            String mediaUrl,
            String thumbnailUrl,
            int mediaSequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(1L)
                .snsCode(snsCode)
                .snsContentId(snsContentId)
                .snsMediaId(snsMediaId)
                .mediaUrl(mediaUrl)
                .thumbnailUrl(thumbnailUrl)
                .contentType(snsCode == SnsPlatform.YOUTUBE ? ContentType.SHORTS : null)
                .sequenceNo(0)
                .mediaSequenceNo(mediaSequenceNo)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void runTransactionsInline() {
        doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void 유튜브_미디어는_addYoutubeContent로_전사한다() {
        runTransactionsInline();
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of(media(
                        SnsPlatform.YOUTUBE, "vid1", "vid1", null, null, 0)));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService).addYoutubeContent(1L, "vid1");
        verify(evaluationService, never()).addContent(any(), any());
        verify(notificationRecorder).recordInAppOnce(
                "APP_QUANT_START", 1L, "지원자 #1 정량 분석을 시작했습니다.");
        verify(notificationRecorder).recordInAppOnce(
                "APP_QUAL_START", 1L, "지원자 #1 정성평가를 시작했습니다.");
        verify(notificationRecorder).recordInAppOnce(
                "APP_QUAL_DONE", 1L, "지원자 #1 정성평가가 완료되었습니다.");
    }

    @Test
    void 인스타_게시물의_각_미디어를_개별_ID와_URL로_addContent한다() {
        runTransactionsInline();
        when(instagramSttClient.isHealthy()).thenReturn(true);
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of(
                        media(SnsPlatform.INSTAGRAM, "post1", "media1",
                                "https://cdn/1.mp4", "https://cdn/1.jpg", 0),
                        media(SnsPlatform.INSTAGRAM, "post1", "media2",
                                "https://cdn/2.jpg", null, 1)));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService).addContent(
                1L, new ContentAddRequest("media1", "https://cdn/1.mp4", "https://cdn/1.jpg"));
        verify(evaluationService).addContent(
                1L, new ContentAddRequest("media2", "https://cdn/2.jpg", null));
        verify(evaluationService, never()).addYoutubeContent(any(), any());
    }

    @Test
    void 인스타_있는데_STT워커_다운이면_처리안하고_실패() {
        when(instagramSttClient.isHealthy()).thenReturn(false);
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of(media(
                        SnsPlatform.INSTAGRAM, "post1", "media1",
                        "https://cdn/1.mp4", null, 0)));

        assertThrows(BusinessException.class, () -> service.analyzeAndReport(1L));

        verify(evaluationService, never()).addContent(any(), any());
        verify(evaluationService, never()).buildReport(any());
    }

    @Test
    void 유튜브_videoId_없으면_skip() {
        runTransactionsInline();
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of(media(
                        SnsPlatform.YOUTUBE, "  ", "missing", null, null, 0)));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService, never()).addYoutubeContent(any(), any());
        verify(evaluationService, never()).addContent(any(), any());
    }

    @Test
    void 유튜브는_Shorts_조회수_상위_3개만_분석한다() {
        runTransactionsInline();
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of(
                        youtube("low", 10L, 0),
                        youtube("top", 400L, 1),
                        youtube("middle", 200L, 2),
                        youtube("second", 300L, 3),
                        youtube("unknown", null, 4)));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService).addYoutubeContent(1L, "top");
        verify(evaluationService).addYoutubeContent(1L, "second");
        verify(evaluationService).addYoutubeContent(1L, "middle");
        verify(evaluationService, never()).addYoutubeContent(1L, "low");
        verify(evaluationService, never()).addYoutubeContent(1L, "unknown");
        verify(evaluationService, times(3)).addYoutubeContent(any(), any());
    }

    @Test
    void 유튜브_긴_영상은_Gemini_영상분석을_건너뛴다() {
        runTransactionsInline();
        ApplicationMedia longForm = ApplicationMedia.builder()
                .applicationId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("long")
                .snsMediaId("long")
                .contentType(ContentType.LONG_FORM)
                .sequenceNo(0)
                .mediaSequenceNo(0)
                .viewCount(1_000L)
                .build();
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of(longForm));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService, never()).addYoutubeContent(any(), any());
        verify(evaluationService).buildReport(1L);
    }

    private ApplicationMedia youtube(String videoId, Long viewCount, int sequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId(videoId)
                .snsMediaId(videoId)
                .contentType(ContentType.SHORTS)
                .sequenceNo(sequenceNo)
                .mediaSequenceNo(0)
                .viewCount(viewCount)
                .build();
    }
}
