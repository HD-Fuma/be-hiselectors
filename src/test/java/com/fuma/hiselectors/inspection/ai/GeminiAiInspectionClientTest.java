package com.fuma.hiselectors.inspection.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import com.fuma.hiselectors.inspection.config.ContentInspectionAnalysisProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

class GeminiAiInspectionClientTest {

    private MockRestServiceServer server;
    private GeminiAiInspectionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ContentInspectionAnalysisProperties properties = properties();
        client = new GeminiAiInspectionClient(
                properties, new ContentInspectionGeminiRequestExecutor(properties),
                new ObjectMapper(),
                new InspectionPromptProvider(), builder.build());
    }

    @Test
    void mapsHttp429ToQuotaExceeded() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .endsWith("/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.inspectText("검수할 콘텐츠"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED);
                            assertThat(exception.getErrorCode().getStatus())
                                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                            assertThat(exception).hasMessage(
                                    "AI 콘텐츠 검수 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
                        });
        server.verify();
    }

    @Test
    void mapsOtherHttpErrorsToInspectionFailed() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .endsWith("/models/test-model:generateContent"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.inspectText("검수할 콘텐츠"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.AI_CONTENT_INSPECTION_FAILED));
        server.verify();
    }

    @Test
    void mapsDetailedReportAndExecutionMetadata() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String inspectionJson = objectMapper.writeValueAsString(Map.of(
                "report", Map.of(
                        "overview", Map.of(
                                "summary", "summary",
                                "purpose", "purpose",
                                "flow", "flow",
                                "overallAssessment", "assessment"),
                        "insight", Map.of(
                                "contentStyle", "review",
                                "tone", "calm",
                                "strengths", List.of("clear"),
                                "cautions", List.of("sponsorship"),
                                "risks", List.of("overclaim"),
                                "hateConfirmed", false,
                                "collabBrands", List.of("brand-a"))),
                "violations", List.of()));
        String geminiResponse = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", inspectionJson))))),
                "modelVersion", "gemini-response-model",
                "usageMetadata", Map.of(
                        "promptTokenCount", 10,
                        "candidatesTokenCount", 20,
                        "totalTokenCount", 30)));
        server.expect(request -> assertThat(request.getURI().getPath())
                        .endsWith("/models/test-model:generateContent"))
                .andRespond(withSuccess(geminiResponse, MediaType.APPLICATION_JSON));

        var result = client.inspectText("content");

        assertThat(result.report().overview().summary()).isEqualTo("summary");
        assertThat(result.report().insight().contentStyle()).isEqualTo("review");
        assertThat(result.report().insight().strengths()).containsExactly("clear");
        assertThat(result.executionMetadata())
                .containsEntry("provider", "GEMINI")
                .containsEntry("requestedModel", "test-model")
                .containsEntry("responseModel", "gemini-response-model")
                .containsEntry("promptVersion", "content-inspection-v4");
        assertThat(result.executionMetadata().get("tokens"))
                .isEqualTo(Map.of("input", 10, "output", 20, "total", 30));
        server.verify();
    }

    @Test
    void exposesSegmentReferenceSchemaWithoutDuplicatedMediaCoordinates() throws Exception {
        String schema = new ObjectMapper().writeValueAsString(client.responseJsonSchema());

        assertThat(schema)
                .contains("targetKind", "coordinateSpace", "segmentId",
                        "CONTENT_MEDIA_SEGMENT", "UTF16_CODE_UNIT")
                .doesNotContain("startTime", "endTime", "bbox");
    }

    private ContentInspectionAnalysisProperties properties() {
        return new ContentInspectionAnalysisProperties(
                "test-key", null, null, "test-model", null);
    }
}
