package com.fuma.hiselectors.creator.dto;

import java.math.BigDecimal;

/**
 * 한 계정이 특정 카테고리에서 차지하는 조회수 비중 합.
 *
 * <p>대표 카테고리는 {@code totalShare} 가 가장 큰 카테고리로 정한다.
 */
public record CategoryShare(String categoryCode, BigDecimal totalShare) {
}
