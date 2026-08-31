package com.fuma.hiselectors.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.matching.service.MatchScorer.Signal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchScorerTest {

    @Test
    void topOnBothSignalsScoresFullAndBottomScoresZero() {
        int[] scores = MatchScorer.score(List.of(
                new Signal(1_000_000, 0.2),
                new Signal(0, 0.0)));

        assertThat(scores[0]).isEqualTo(100);
        assertThat(scores[1]).isEqualTo(0);
    }

    @Test
    void weightsSalesAndConversionSeparately() {
        // A: 매출 최고·전환 최저, B: 매출 최저·전환 최고 → 가중치(0.6 / 0.4)만 남는다.
        int[] scores = MatchScorer.score(List.of(
                new Signal(500, 0.0),
                new Signal(0, 0.5)));

        assertThat(scores[0]).isEqualTo(60);
        assertThat(scores[1]).isEqualTo(40);
    }

    @Test
    void allEqualValuesGetTheSameTopScoreInsteadOfCollapsing() {
        int[] scores = MatchScorer.score(List.of(
                new Signal(300, 0.1),
                new Signal(300, 0.1),
                new Signal(300, 0.1)));

        assertThat(scores).containsExactly(100, 100, 100);
    }
}
