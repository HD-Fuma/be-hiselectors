package com.fuma.hiselectors.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.stt.ContentAddRequest;
import com.fuma.hiselectors.stt.CreatorEvaluationService;
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
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final ApplicationAnalysisService service = new ApplicationAnalysisService(
            mediaRepository, applicationRepository, evaluationService, transactionTemplate);

    private ApplicationMedia media(SnsPlatform snsCode, String snsContentId, String mediaUrl) {
        return ApplicationMedia.builder()
                .applicationId(1L)
                .snsCode(snsCode)
                .snsContentId(snsContentId)
                .mediaUrl(mediaUrl)
                .sequenceNo(0)
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
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(1L))
                .thenReturn(List.of(media(SnsPlatform.YOUTUBE, "vid1", null)));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService).addYoutubeContent(1L, "vid1");
        verify(evaluationService, never()).addContent(any(), any());
    }

    @Test
    void 인스타_미디어는_media_url로_addContent한다() {
        runTransactionsInline();
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(1L))
                .thenReturn(List.of(media(SnsPlatform.INSTAGRAM, "mediaId1", "https://cdn/x.jpg")));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService).addContent(eq(1L), any(ContentAddRequest.class));
        verify(evaluationService, never()).addYoutubeContent(any(), any());
    }

    @Test
    void 유튜브_videoId_없으면_skip() {
        runTransactionsInline();
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(1L))
                .thenReturn(List.of(media(SnsPlatform.YOUTUBE, "  ", null)));
        when(evaluationService.buildReport(1L)).thenReturn(mock(ApplicationReport.class));
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        service.analyzeAndReport(1L);

        verify(evaluationService, never()).addYoutubeContent(any(), any());
        verify(evaluationService, never()).addContent(any(), any());
    }
}
