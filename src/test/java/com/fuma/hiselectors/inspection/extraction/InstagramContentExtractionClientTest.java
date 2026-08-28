package com.fuma.hiselectors.inspection.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class InstagramContentExtractionClientTest {

    private MockRestServiceServer server;
    private InstagramContentExtractionClient client;

    @BeforeEach
    void setUp() {
        InspectionExtractionProperties properties = new InspectionExtractionProperties(
                new InspectionExtractionProperties.Instagram(
                        "content-whisper", "rapid-ocr", "http://worker"),
                null);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InstagramContentExtractionClient(properties, builder.build());
    }

    @Test
    void usesContentSpecificWorkerContract() {
        server.expect(request -> assertThat(request.getURI().toString())
                        .isEqualTo("http://worker/content/reel"))
                .andRespond(withSuccess("""
                        {
                          "schemaVersion":"1.0",
                          "stt":{"language":"ko","segments":[
                            {"segmentId":"stt-001","startMs":0,"endMs":500,"text":"발화"}
                          ]},
                          "ocr":{"segments":[]},
                          "visual":{"segments":[]}
                        }
                        """, MediaType.APPLICATION_JSON));

        ContentExtractionExecutionResult result = client.extract(
                "https://cdn.example.com/reel.mp4", null);

        assertThat(result.extraction().stt().segments()).hasSize(1);
        assertThat(result.selectedModel()).isEqualTo("content-whisper");
        server.verify();
    }
}
