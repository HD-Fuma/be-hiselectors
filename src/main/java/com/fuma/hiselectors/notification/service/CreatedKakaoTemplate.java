package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;

// 완성된 카카오 DTO와 DB에 기록할 본문 반환
public record CreatedKakaoTemplate(KakaoMessageTemplate template, String body) {
}
