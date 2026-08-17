package com.fuma.hiselectors.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock applicationClock() {
        return Clock.system(SEOUL_ZONE);
    }
}
