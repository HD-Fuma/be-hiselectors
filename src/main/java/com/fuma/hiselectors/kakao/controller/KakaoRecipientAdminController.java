package com.fuma.hiselectors.kakao.controller;

import com.fuma.hiselectors.kakao.dto.KakaoRecipientAdminResponse;
import com.fuma.hiselectors.kakao.service.KakaoRecipientAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카카오 수신 현황", description = "관리자용 셀렉터스 카카오 메시지 수신 현황 조회")
@RestController
@RequestMapping("/api/admin/kakao/recipients")
@RequiredArgsConstructor
@Validated
public class KakaoRecipientAdminController {
    private final KakaoRecipientAdminService service;

    @Operation(summary = "셀렉터스 카카오 수신 현황 조회")
    @GetMapping
    public ResponseEntity<Page<KakaoRecipientAdminResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(service.search(keyword, status, page, size));
    }
}
