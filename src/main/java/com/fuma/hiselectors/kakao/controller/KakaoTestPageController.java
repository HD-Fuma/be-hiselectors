package com.fuma.hiselectors.kakao.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Profile("local")
@Controller
public class KakaoTestPageController {

    @GetMapping("/kakao-test")
    public ResponseEntity<Resource> page() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("kakao-test/index.html"));
    }
}
