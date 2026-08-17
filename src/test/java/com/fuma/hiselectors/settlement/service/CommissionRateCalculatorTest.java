package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CommissionRateCalculatorTest {

    private final CommissionRateCalculator calculator = new CommissionRateCalculator();

    @ParameterizedTest
    @CsvSource({
            "0,3.00", "5000,3.00", "5001,5.00", "10000,5.00",
            "10001,7.00", "50000,7.00", "50001,10.00"
    })
    void calculatesYoutubeRate(long followers, String expected) {
        assertThat(calculator.calculate(SnsPlatform.YOUTUBE, followers))
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "0,3.00", "10000,3.00", "10001,5.00", "50000,5.00",
            "50001,7.00", "100000,7.00", "100001,10.00"
    })
    void calculatesInstagramRate(long followers, String expected) {
        assertThat(calculator.calculate(SnsPlatform.INSTAGRAM, followers))
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @CsvSource({"YOUTUBE", "INSTAGRAM"})
    void nullFollowersUseMinimumRate(SnsPlatform platform) {
        assertThat(calculator.calculate(platform, null)).isEqualByComparingTo("3.00");
    }
}
