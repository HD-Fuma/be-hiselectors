package com.fuma.hiselectors.stt.test;

import jakarta.validation.constraints.NotBlank;

public record YoutubeSttTestRequest(
        @NotBlank(message = "videoId는 필수입니다.") String videoId) {
}
