package com.fuma.hiselectors.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

class CreatorEvaluationServiceTest {

    private final GeminiEvalClient evalClient = mock(GeminiEvalClient.class);
    private final ApplicationContentAnalysisRepository repository =
            mock(ApplicationContentAnalysisRepository.class);
    private final ApplicationMediaRepository mediaRepository = mock(ApplicationMediaRepository.class);
    private final CreatorEvaluationService service = new CreatorEvaluationService(
            mock(InstagramSttClient.class), mock(YoutubeSttClient.class), evalClient,
            repository, mediaRepository, mock(ApplicationReportRepository.class),
            mock(com.fuma.hiselectors.application.repository.ApplicationRepository.class),
            mock(com.fuma.hiselectors.selectors.repository.SelectorsRepository.class),
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
                "요약", "BEAUTY", List.of("스킨케어", "리뷰"),
                "style", "tone", List.of("강점"), List.of(), List.of(), false, List.of()));
    }

    @Test
    void 조회수_최고_콘텐츠를_대표로_뽑고_그_분석을_역정규화한다() {
        stubInsight();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(
                analysis("a", "BEAUTY", "립스틱,파운데이션"),
                analysis("b", "FOOD", "맛집,디저트")));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L)).thenReturn(List.of(
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
    void 조회수_최고인_콘텐츠는_분석행이_없어도_대표로_선정되고_카테고리는_비어있다() {
        // 대표 콘텐츠는 STT/OCR·Gemini 분석 성공 여부와 무관하게 항상 노출돼야 하므로,
        // 분석행이 없는 b(조회수 최고)가 대표가 되고 카테고리·키워드만 비어 있다.
        stubInsight();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(
                analysis("a", "BEAUTY", "립스틱")));   // 분석된 건 a 뿐. b(조회수 최고)는 분석행 없음
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L)).thenReturn(List.of(
                media("a", 100L, ContentType.SHORT_FORM),
                media("b", 5000L, ContentType.FEED)));

        ApplicationReport report = service.buildReport(1L);

        assertThat(report.getRepresentativeContentUrl()).isEqualTo("https://insta/b");
        assertThat(report.getRepresentativeContentType()).isEqualTo("FEED");
        assertThat(report.getRepresentativeViewCount()).isEqualTo(5000L);
        assertThat(report.getRepresentativeCategory()).isNull();
        assertThat(report.getRepresentativeKeywords()).isNull();
    }

    @Test
    void 조회수가_전부_null이면_대표선정이_깨지지_않는다() {
        stubInsight();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(analysis("a", "BEAUTY", "립스틱")));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L)).thenReturn(List.of(
                media("a", null, ContentType.SHORT_FORM)));

        ApplicationReport report = service.buildReport(1L);

        assertThat(report.getRepresentativeContentUrl()).isEqualTo("https://insta/a");
        assertThat(report.getRepresentativeViewCount()).isNull();
    }

    @Test
    void Gemini_리포트_입력은_가중치로_줄이면서_각_소스의_끝도_보존한다() {
        stubInsight();
        ApplicationContentAnalysis longAnalysis = ApplicationContentAnalysis.builder()
                .applicantId(1L).contentKey("long").source("instagram")
                .stt("가".repeat(7_000) + "STT_END")
                .ocr("나".repeat(3_000) + "OCR_END")
                .hateSuspected(false)
                .build();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(longAnalysis));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of());

        service.buildReport(1L);

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(evalClient).insight(input.capture());
        assertThat(input.getValue()).hasSizeLessThanOrEqualTo(4_000)
                .contains("STT_END", "OCR_END");
    }

    @Test
    void 예산_이내면_원문을_전부_쓰고_임베딩_워커를_호출하지_않는다() {
        ApplicationContentAnalysis row = ApplicationContentAnalysis.builder()
                .applicantId(1L).contentKey("short").source("instagram")
                .stt("가중치 표현과 다른 완곡한 건강 효능 주장")
                .ocr("화면 가격 10000원").hateSuspected(false).build();
        AtomicBoolean called = new AtomicBoolean();

        WeightedTranscriptSelector.Selection selected = WeightedTranscriptSelector.select(
                List.of(row), List.of(), 6_000, texts -> {
                    called.set(true);
                    return List.of();
                });

        assertThat(selected.text()).contains(row.getStt(), row.getOcr());
        assertThat(selected.truncated()).isFalse();
        assertThat(selected.rankingAttempted()).isFalse();
        assertThat(called).isFalse();
    }

    @Test
    void 이천자를_넘으면_최종상한_이내여도_임베딩으로_줄인다() {
        String mediumText = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> String.valueOf((char) ('가' + i)).repeat(250) + ".")
                .collect(java.util.stream.Collectors.joining(" "));
        ApplicationContentAnalysis row = ApplicationContentAnalysis.builder()
                .applicantId(1L).contentKey("medium").source("instagram")
                .stt(mediumText)
                .ocr("").hateSuspected(false).build();
        AtomicBoolean called = new AtomicBoolean();

        WeightedTranscriptSelector.Selection selected = WeightedTranscriptSelector.select(
                List.of(row), List.of(), 4_000, texts -> {
                    called.set(true);
                    return java.util.stream.IntStream.range(0, texts.size()).boxed().toList();
                });

        assertThat(called).isTrue();
        assertThat(selected.text()).hasSizeLessThanOrEqualTo(2_000);
        assertThat(selected.truncated()).isTrue();
    }

    @Test
    void 입력_예산이_작아도_콘텐츠별_대표_구간을_우선_보존한다() {
        List<ApplicationContentAnalysis> rows = List.of(
                analysis("a", "BEAUTY", "립스틱"),
                analysis("b", "FOOD", "디저트"),
                analysis("c", "TRAVEL", "여행"));

        WeightedTranscriptSelector.Selection selected =
                WeightedTranscriptSelector.select(rows, List.of(), 200);

        assertThat(selected.text()).contains("a", "b", "c");
        assertThat(selected.selectedContents()).isEqualTo(3);
        assertThat(selected.text().length()).isLessThanOrEqualTo(200);
    }

    @Test
    void 광고와_위험_신호가_있는_문장은_짧은_예산에서도_선택한다() {
        ApplicationContentAnalysis row = ApplicationContentAnalysis.builder()
                .applicantId(1L).contentKey("weighted").source("instagram")
                .stt("일상 이야기입니다. 이 제품은 협찬 광고이며 부작용 주의가 필요합니다. 오늘도 감사합니다.")
                .ocr("").keywords("제품").hateSuspected(false).build();

        WeightedTranscriptSelector.Selection selected =
                WeightedTranscriptSelector.select(List.of(row), List.of(), 45);

        assertThat(selected.text()).contains("협찬", "부작용", "주의");
    }

    @Test
    void Gemini_토큰_사용량_메타데이터를_파싱한다() {
        String json = """
                {"candidates":[],"modelVersion":"gemini-3.5-flash-lite",
                "usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":30,
                "thoughtsTokenCount":10,"totalTokenCount":160}}
                """;

        GeminiEvalClient.GeminiResponse response = new tools.jackson.databind.ObjectMapper()
                .readValue(json, GeminiEvalClient.GeminiResponse.class);

        assertThat(response.usageMetadata().promptTokenCount()).isEqualTo(120);
        assertThat(response.usageMetadata().candidatesTokenCount()).isEqualTo(30);
        assertThat(response.usageMetadata().thoughtsTokenCount()).isEqualTo(10);
        assertThat(response.usageMetadata().totalTokenCount()).isEqualTo(160);
        assertThat(response.modelVersion()).isEqualTo("gemini-3.5-flash-lite");
    }

    @Test
    void YouTube_Gemini_토큰_사용량_메타데이터를_파싱한다() {
        String json = """
                {"candidates":[],"promptFeedback":{},"modelVersion":"gemini-3.6-flash",
                "usageMetadata":{
                "promptTokenCount":320,"candidatesTokenCount":80,
                "thoughtsTokenCount":20,"totalTokenCount":420}}
                """;

        YoutubeSttClient.GeminiResponse response = new tools.jackson.databind.ObjectMapper()
                .readValue(json, YoutubeSttClient.GeminiResponse.class);

        assertThat(response.usageMetadata().promptTokenCount()).isEqualTo(320);
        assertThat(response.usageMetadata().candidatesTokenCount()).isEqualTo(80);
        assertThat(response.usageMetadata().thoughtsTokenCount()).isEqualTo(20);
        assertThat(response.usageMetadata().totalTokenCount()).isEqualTo(420);
        assertThat(response.modelVersion()).isEqualTo("gemini-3.6-flash");
    }

    @Test
    void 안전_구간_다음의_남은_예산은_임베딩_MMR_순서로_채운다() {
        ApplicationContentAnalysis row = ApplicationContentAnalysis.builder()
                .applicantId(1L).contentKey("semantic").source("instagram")
                .stt("첫 구간 " + "가".repeat(15) + ". "
                        + "낮은 순위 " + "나".repeat(15) + ". "
                        + "의미 우선 " + "다".repeat(15) + ". "
                        + "끝 구간 " + "라".repeat(15) + ".")
                .ocr("").hateSuspected(false).build();

        WeightedTranscriptSelector.Selection selected = WeightedTranscriptSelector.select(
                List.of(row), List.of(), 72, texts -> List.of(2, 1, 0, 3));

        assertThat(selected.semanticRanking()).isTrue();
        assertThat(selected.truncated()).isTrue();
        assertThat(selected.text()).contains("첫 구간", "의미 우선", "끝 구간")
                .doesNotContain("낮은 순위");
    }

    @Test
    void 위험_신호는_임베딩_순위와_무관하게_앞뒤_문맥까지_보존한다() {
        ApplicationContentAnalysis row = ApplicationContentAnalysis.builder()
                .applicantId(1L).contentKey("risk").source("instagram")
                .stt("첫 문장입니다. 앞 문맥입니다. 일주일에 5kg 빠지는 효능입니다. "
                        + "뒤 문맥입니다. 마지막 문장입니다.")
                .ocr("").hateSuspected(false).build();

        WeightedTranscriptSelector.Selection selected = WeightedTranscriptSelector.select(
                List.of(row), List.of(), 70, texts -> List.of());

        assertThat(selected.text()).contains("앞 문맥", "5kg", "효능", "뒤 문맥");
    }

    @Test
    void 로컬_카테고리와_키워드가_없으면_Gemini_결과로_채운다() {
        stubInsight();
        when(repository.findByApplicantId(1L)).thenReturn(List.of(
                analysis("a", null, null)));
        when(mediaRepository.findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .thenReturn(List.of());

        ApplicationReport report = service.buildReport(1L);

        assertThat(report.getCategory()).isEqualTo("BEAUTY");
        assertThat(report.getKeywords()).isEqualTo("스킨케어, 리뷰");
    }
}
