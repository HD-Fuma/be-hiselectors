package com.fuma.hiselectors.settlement.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class CommissionRateCalculator {

    public BigDecimal calculate(SnsPlatform platform, Long followerCount) {
        long followers = followerCount == null ? 0L : followerCount;
        return switch (platform) {
            case YOUTUBE -> youtubeRate(followers);
            case INSTAGRAM -> instagramRate(followers);
        };
    }

    private BigDecimal youtubeRate(long followers) {
        if (followers <= 5_000) {
            return rate("3.00");
        }
        if (followers <= 10_000) {
            return rate("5.00");
        }
        if (followers <= 50_000) {
            return rate("7.00");
        }
        return rate("10.00");
    }

    private BigDecimal instagramRate(long followers) {
        if (followers <= 10_000) {
            return rate("3.00");
        }
        if (followers <= 50_000) {
            return rate("5.00");
        }
        if (followers <= 100_000) {
            return rate("7.00");
        }
        return rate("10.00");
    }

    private BigDecimal rate(String value) {
        return new BigDecimal(value);
    }
}
