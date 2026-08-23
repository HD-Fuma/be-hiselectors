package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.ApplicationContentAnalysis;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationContentAnalysisRepository;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationReportRepository;
import com.fuma.hiselectors.application.service.LocalAnalyzerClient;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.creator.discovery.MetaGraphApiClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class CreatorEvaluationServiceTest {

    private final GeminiEvalClient evalClient = mock(GeminiEvalClient.class);
    private final ApplicationContentAnalysisRepository repository =
            mock(ApplicationContentAnalysisRepository.class);
    private final ApplicationMediaRepository mediaRepository = mock(ApplicationMediaRepository.class);
    private final CreatorEvaluationService service = new CreatorEvaluationService(
            mock(InstagramSttClient.class), mock(YoutubeSttClient.class), evalClient,
            repository, mediaRepository, mock(ApplicationReportRepository.class),
            mock(MetaGraphApiClient.class), mock(LocalAnalyzerClient.class),
            mock(TransactionTemplate.class));

    private ApplicationContentAnalysis analysis(String contentKey, String category, String keywords) {
        return ApplicationContentAnalysis.builder()
                .applicantId(1L).contentKey(contentKey).source("instagram")
                .stt("말하는 내용 " + contentKey).ocr("자막 " + contentKey)
                .category(category).keywords(keywords).hateSuspected(false)
                .build();
    }

    private ApplicationMedia media(String snsContentId, Long viewCount, ContentType type) {
        return ApplicationMedia.builder()
                .applicationId(1L).snsCode(SnsPlatform.INSTAGRAM).snsContentId(snsContentId)
                .contentUrl("https://insta/" + snsContentId).contentType(type)
                .viewCount(viewCount).sequenceNo(0).build();
    }

    private void stubInsight() {
        when(evalClient.insight(any())).thenReturn(new ApplicantInsight(
                "요약", "style", "tone", List.of("강점"), List.of(), List.of(), false, List.of()));
    }

    @Test
    void 조회수_최고_콘텐츠를_대표로_뽑고_그_분석을_역정규화한다() {
        stubInsight();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(
                analysis("a", "BEAUTY", "립스틱,파운데이션"),
                analysis("b", "FOOD", "맛집,디저트")));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(1L)).thenReturn(List.of(
                media("a", 100L, ContentType.SHORT_FORM),
                media("b", 5000L, ContentType.FEED)));   // b 가 조회수 최고

        ApplicationReport report = service.buildReport(1L);

        assertThat(report.getRepresentativeContentUrl()).isEqualTo("https://insta/b");
        assertThat(report.getRepresentativeContentType()).isEqualTo("FEED");
        assertThat(report.getRepresentativeViewCount()).isEqualTo(5000L);
        assertThat(report.getRepresentativeCategory()).isEqualTo("FOOD");
        assertThat(report.getRepresentativeKeywords()).isEqualTo("맛집,디저트");
    }

    @Test
    void 조회수_최고라도_분석행이_없으면_대표에서_제외되고_분석된_콘텐츠가_대표() {
        stubInsight();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(
                analysis("a", "BEAUTY", "립스틱")));   // 분석된 건 a 뿐. b(조회수 최고)는 분석행 없음
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(1L)).thenReturn(List.of(
                media("a", 100L, ContentType.SHORT_FORM),
                media("b", 5000L, ContentType.FEED)));

        ApplicationReport report = service.buildReport(1L);

        assertThat(report.getRepresentativeContentUrl()).isEqualTo("https://insta/a");
        assertThat(report.getRepresentativeCategory()).isEqualTo("BEAUTY");
        assertThat(report.getRepresentativeKeywords()).isEqualTo("립스틱");
    }

    @Test
    void 조회수가_전부_null이면_대표선정이_깨지지_않는다() {
        stubInsight();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(analysis("a", "BEAUTY", "립스틱")));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(1L)).thenReturn(List.of(
                media("a", null, ContentType.SHORT_FORM)));

        ApplicationReport report = service.buildReport(1L);

        assertThat(report.getRepresentativeContentUrl()).isEqualTo("https://insta/a");
        assertThat(report.getRepresentativeViewCount()).isNull();
    }
}
