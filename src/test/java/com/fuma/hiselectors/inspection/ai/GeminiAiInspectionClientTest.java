package com.fuma.hiselectors.inspection.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import com.fuma.hiselectors.stt.GeminiProperties;
import com.fuma.hiselectors.stt.GeminiRequestExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GeminiAiInspectionClientTest {

    private MockRestServiceServer server;
    private GeminiAiInspectionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        GeminiProperties properties = properties();
        client = new GeminiAiInspectionClient(properties, new GeminiRequestExecutor(properties),
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

    private GeminiProperties properties() {
        return new GeminiProperties(
                "test-key", null, null,
                "test-model", "test-model", "test-model", null, null);
    }
}
