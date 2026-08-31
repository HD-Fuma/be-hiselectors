package com.fuma.hiselectors.application.service;

/** 지원서가 저장·커밋되면 발행된다. 즉시 미디어 수집·분석을 트리거하는 데 쓴다. */
public record ApplicationCreatedEvent(Long applicationId) {
}
