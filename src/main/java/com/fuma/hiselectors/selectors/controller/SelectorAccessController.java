package com.fuma.hiselectors.selectors.controller;

import com.fuma.hiselectors.selectors.dto.SelectorAccessResponse;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/selector-access")
@Tag(name = "셀렉터스 활동", description = "로그인 사용자의 셀렉터스 접근 권한 조회 및 활동 종료")
public class SelectorAccessController {

    private final SelectorAccessService selectorAccessService;

    @GetMapping
    public ResponseEntity<SelectorAccessResponse> getAccess(Principal principal) {
        return ResponseEntity.ok(selectorAccessService.getAccess(principal.getName()));
    }

    @DeleteMapping
    public ResponseEntity<Void> endActivity(Principal principal) {
        selectorAccessService.endActivity(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
