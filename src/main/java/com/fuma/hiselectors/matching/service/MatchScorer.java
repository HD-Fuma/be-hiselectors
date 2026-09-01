package com.fuma.hiselectors.matching.service;

import java.util.List;

/**
 * 추천 점수 계산. 매출(최근성 가중)과 전환율을 후보 집단 내 min-max 정규화해 가중 합산한다.
 * 단위가 다른 두 신호(원 vs 비율)를 상대 순위로 섞기 위한 최소한의 정규화.
 */
final class MatchScorer {

    static final double SALES_WEIGHT = 0.6;
    static final double CONVERSION_WEIGHT = 0.4;

    private MatchScorer() {
    }

    /** 후보별 recency 가중 매출과 전환율을 받아 0~100 점수를 반환한다(입력과 같은 순서). */
    static int[] score(List<Signal> signals) {
        int size = signals.size();
        int[] scores = new int[size];
        if (size == 0) {
            return scores;
        }
        double[] recency = signals.stream().mapToDouble(Signal::recencyWeightedSales).toArray();
        double[] conversion = signals.stream().mapToDouble(Signal::conversionRate).toArray();
        double[] normRecency = normalize(recency);
        double[] normConversion = normalize(conversion);
        for (int i = 0; i < size; i++) {
            double composite = SALES_WEIGHT * normRecency[i] + CONVERSION_WEIGHT * normConversion[i];
            scores[i] = (int) Math.round(composite * 100);
        }
        return scores;
    }

    /** min-max 정규화. 모든 값이 같으면(변별 불가) 1.0 으로 통일한다. */
    private static double[] normalize(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double range = max - min;
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = range == 0 ? 1.0 : (values[i] - min) / range;
        }
        return result;
    }

    record Signal(double recencyWeightedSales, double conversionRate) {
    }
}
