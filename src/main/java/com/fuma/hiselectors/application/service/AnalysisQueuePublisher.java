package com.fuma.hiselectors.application.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@ConditionalOnProperty(name = "application.content-analysis.queue-url")
@Slf4j
public class AnalysisQueuePublisher {

    private final String queueUrl;
    private final SqsClient sqsClient;

    @Autowired
    public AnalysisQueuePublisher(
            @Value("${application.content-analysis.queue-url}") String queueUrl,
            @Value("${media.s3.region}") String region) {
        this(queueUrl, SqsClient.builder().region(Region.of(region)).build());
    }

    AnalysisQueuePublisher(String queueUrl, SqsClient sqsClient) {
        this.queueUrl = queueUrl;
        this.sqsClient = sqsClient;
    }

    public void publish(Long applicationId) {
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(applicationId.toString())
                    .messageGroupId("analysis")
                    .messageDeduplicationId(applicationId.toString())
                    .build());
        } catch (RuntimeException e) {
            log.warn("분석 큐 발행 실패, 복구 스케줄에서 재처리합니다: applicationId={}",
                    applicationId, e);
        }
    }

    @PreDestroy
    void close() {
        sqsClient.close();
    }
}
