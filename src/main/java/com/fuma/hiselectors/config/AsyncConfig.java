package com.fuma.hiselectors.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** 지원서 제출 직후 미디어 수집·분석을 백그라운드로 즉시 실행하기 위한 @Async 활성화. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
}
