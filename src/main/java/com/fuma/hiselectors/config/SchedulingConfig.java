package com.fuma.hiselectors.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 애플리케이션의 {@code @Scheduled} 작업을 활성화한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
