package com.fuma.hiselectors.stt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 지원자별 콘텐츠 분석 결과를 휘발성(TTL)으로 모은다. 여러 콘텐츠를 쌓았다가 평가 시점에 하나로 축약.
 * DB에 영속하지 않는다 — 비동의 후보 원문 미보관(무저장 원칙). 평가 후 clear, 미평가분은 TTL로 소멸.
 */
@Component
public class TranscriptCache {

    private final Cache<Long, List<InstagramAnalysisResult>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(6))
            .maximumSize(10_000)
            .build();

    public void add(Long applicantId, InstagramAnalysisResult result) {
        // compute 는 키 단위 원자적 — 동시 add 에도 리스트가 유실되지 않는다.
        cache.asMap().compute(applicantId, (id, list) -> {
            List<InstagramAnalysisResult> acc = list == null ? new ArrayList<>() : list;
            acc.add(result);
            return acc;
        });
    }

    public List<InstagramAnalysisResult> get(Long applicantId) {
        List<InstagramAnalysisResult> list = cache.getIfPresent(applicantId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void clear(Long applicantId) {
        cache.invalidate(applicantId);
    }
}
