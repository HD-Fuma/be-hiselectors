package com.fuma.hiselectors.content.client;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 외부 API(Instagram, Youtube) 호출용 HTTP 설정 */
@Configuration
public class ContentClientConfig {

    @Bean
    public RestClient contentRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 외부 서버 연결 제한 시간
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // 연결 후 응답 데이터 수신 제한 시간
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }
}
