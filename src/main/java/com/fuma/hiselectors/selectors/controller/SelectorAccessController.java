package com.fuma.hiselectors.selectors.controller;

import com.fuma.hiselectors.selectors.dto.SelectorAccessResponse;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
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
