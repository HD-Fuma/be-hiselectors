package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class AnalysisQueuePublisherTest {

    @Test
    void publishesApplicationIdToSingleFifoGroup() {
        SqsClient sqsClient = mock(SqsClient.class);
        AnalysisQueuePublisher publisher = new AnalysisQueuePublisher(
                "https://sqs.ap-northeast-2.amazonaws.com/123/analysis.fifo", sqsClient);

        publisher.publish(310L);

        ArgumentCaptor<SendMessageRequest> request =
                ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(request.capture());
        assertThat(request.getValue().messageBody()).isEqualTo("310");
        assertThat(request.getValue().messageGroupId()).isEqualTo("analysis");
        assertThat(request.getValue().messageDeduplicationId()).isEqualTo("310");
    }

    @Test
    void leavesHourlyRecoveryToHandleQueueFailure() {
        SqsClient sqsClient = mock(SqsClient.class);
        AnalysisQueuePublisher publisher = new AnalysisQueuePublisher("queue-url", sqsClient);
        doThrow(new IllegalStateException("SQS unavailable"))
                .when(sqsClient).sendMessage(org.mockito.ArgumentMatchers.any(SendMessageRequest.class));

        assertThatCode(() -> publisher.publish(310L)).doesNotThrowAnyException();
    }
}
