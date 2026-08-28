package com.fuma.hiselectors.inspection.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class YoutubeContentExtractionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private YoutubeContentExtractionClient client;

    @BeforeEach
    void setUp() {
        InspectionExtractionProperties properties = new InspectionExtractionProperties(
                new InspectionExtractionProperties.Instagram("stt", "ocr"),
                new InspectionExtractionProperties.Youtube(
                        "test-key", null, "gemini-test", null, 2048, "v1beta"));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeContentExtractionClient(
                properties,
                new ContentGeminiRequestExecutor(properties),
                new InspectionPromptProvider(),
                objectMapper,
                builder.build());
    }

    @Test
    void extractsStructuredEvidenceWithStatelessInteraction() throws Exception {
        String output = """
                {
                  "schemaVersion":"1.0",
                  "stt":{"language":"ko","segments":[
                    {"segmentId":"stt-001","startMs":100,"endMs":900,"text":"발화"}
                  ]},
                  "ocr":{"segments":[
                    {"segmentId":"ocr-001","startMs":200,"endMs":800,
                     "text":"화면 글자","coordinateSpace":"NORMALIZED",
                     "bbox":{"x":0.1,"y":0.2,"width":0.3,"height":0.1}}
                  ]},
                  "visual":{"segments":[
                    {"segmentId":"visual-001","startMs":0,"endMs":1000,
                     "description":"사람이 제품을 들어 보인다."}
                  ]}
                }
                """;
        String response = """
                {
                  "id":"interaction-1",
                  "model":"gemini-test-001",
                  "status":"completed",
                  "steps":[{"type":"model_output","content":[
                    {"type":"text","text":%s}
                  ]}],
                  "usage":{"total_input_tokens":10,"total_output_tokens":20,
                    "total_thought_tokens":3,"total_tokens":33}
                }
                """.formatted(objectMapper.writeValueAsString(output));

        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v1beta/interactions");
                    assertThat(request.getHeaders().getFirst("x-goog-api-key"))
                            .isEqualTo("test-key");
                })
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    assertThat(body).contains("\"store\":false")
                            .contains("https://www.youtube.com/watch?v=video-1")
                            .contains("\"response_format\"")
                            .contains("\"NORMALIZED\"");
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        ContentExtractionExecutionResult result = client.extract("video-1");

        assertThat(result.providerRequestId()).isEqualTo("interaction-1");
        assertThat(result.extraction().stt().segments()).hasSize(1);
        assertThat(result.extraction().ocr().segments()).hasSize(1);
        assertThat(result.extraction().visual().segments()).hasSize(1);
        assertThat(result.totalTokens()).isEqualTo(33);
        server.verify();
    }
}
