package com.fuma.hiselectors.category.bootstrap;

import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryDataService.InitializationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 애플리케이션 시작 시 기본 발굴 카테고리·키워드를 보정한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "discovery.defaults",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DefaultDiscoveryDataInitializer implements ApplicationRunner {

    private final DefaultDiscoveryDataService defaultDiscoveryDataService;

    @Override
    public void run(ApplicationArguments args) {
        InitializationResult result = defaultDiscoveryDataService.initialize();
        log.info("기본 발굴 데이터 초기화 완료: 기준일={}, 카테고리 생성={}, 키워드 생성={}, 유지={}",
                DefaultDiscoveryCatalog.SNAPSHOT_DATE,
                result.createdCategories(),
                result.createdKeywords(),
                result.skippedCategories());
    }
}
