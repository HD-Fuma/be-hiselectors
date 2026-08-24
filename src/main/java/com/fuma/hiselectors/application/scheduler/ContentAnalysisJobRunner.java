package com.fuma.hiselectors.application.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.content-analysis.run-once", havingValue = "true")
public class ContentAnalysisJobRunner implements ApplicationRunner {

    private final ContentAnalysisScheduler scheduler;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) {
        try {
            scheduler.analyzeOne();
        } finally {
            context.close();
        }
    }
}
