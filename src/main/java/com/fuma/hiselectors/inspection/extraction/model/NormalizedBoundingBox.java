package com.fuma.hiselectors.inspection.extraction.model;

public record NormalizedBoundingBox(
        Double x,
        Double y,
        Double width,
        Double height
) {

    public NormalizedBoundingBox {
        if (!valid(x) || !valid(y) || !positive(width) || !positive(height)
                || x + width > 1.0 || y + height > 1.0) {
            throw new IllegalArgumentException("bbox는 0~1 정규화 좌표 범위여야 합니다.");
        }
    }

    private static boolean valid(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static boolean positive(Double value) {
        return valid(value) && value > 0.0;
    }
}
