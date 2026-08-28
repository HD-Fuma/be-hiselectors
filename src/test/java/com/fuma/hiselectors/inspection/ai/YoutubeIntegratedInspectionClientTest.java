package com.fuma.hiselectors.inspection.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.InspectionRuleConfig;
import com.fuma.hiselectors.inspection.config.ContentInspectionAnalysisProperties;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import com.fuma.hiselectors.stt.GeminiProperties;
import com.fuma.hiselectors.stt.GeminiRequestExecutor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class YoutubeIntegratedInspectionClientTest {

    private MockRestServiceServer server;
    private YoutubeIntegratedInspectionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = properties();
        ContentInspectionAnalysisProperties analysisProperties =
                new ContentInspectionAnalysisProperties(
                        "test-key", null, null, "test-model", null);
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiAiInspectionClient inspectionMapper = new GeminiAiInspectionClient(
                analysisProperties,
                new ContentInspectionGeminiRequestExecutor(analysisProperties), objectMapper,
                new InspectionPromptProvider());
        client = new YoutubeIntegratedInspectionClient(
                properties, new GeminiRequestExecutor(properties), objectMapper,
                inspectionMapper, builder.build());
    }

    @Test
    void mapsHttp429ToQuotaExceeded() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .endsWith("/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(this::inspect)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED));
        server.verify();
    }

    @Test
    void mapsOtherHttpErrorsToInspectionFailed() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .endsWith("/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(this::inspect)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_CONTENT_INSPECTION_FAILED));
        server.verify();
    }

    private void inspect() {
        Content content = Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("video-id")
                .contentUrl("https://youtu.be/video-id")
                .contentType(ContentType.LONG_FORM)
                .build();
        ContentMedia video = ContentMedia.create(
                1L, "https://youtu.be/video-id", MediaType.VIDEO, Map.of());
        ReflectionTestUtils.setField(video, "id", 10L);

        client.inspect("video-id", content, video, List.of(video), policy());
    }

    private InspectionPolicy policy() {
        return InspectionPolicy.create(
                SnsPlatform.YOUTUBE,
                "v1",
                new InspectionRuleConfig(List.of(), List.of(), "ref"),
                "rule-hash",
                "test-model",
                "ai-prompt-v1",
                "검수 입력: %s",
                "ai-hash",
                "test-model",
                "test-model",
                "extraction-prompt-v1",
                "영상 내용을 추출하세요.",
                "extraction-hash",
                "config-hash");
    }

    private GeminiProperties properties() {
        return new GeminiProperties(
                "test-key", null, null, "test-model", null, null);
    }
}
