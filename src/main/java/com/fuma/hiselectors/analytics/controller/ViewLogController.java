package com.fuma.hiselectors.analytics.controller;

import com.fuma.hiselectors.analytics.dto.ViewLogRequest;
import com.fuma.hiselectors.analytics.dto.ViewLogResponse;
import com.fuma.hiselectors.analytics.service.ViewLogService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirements
@RestController
@RequestMapping("/api/view-logs")
@RequiredArgsConstructor
public class ViewLogController {

    private final ViewLogService viewLogService;

    @PostMapping
    public ResponseEntity<ViewLogResponse> record(Principal principal,
            @Valid @RequestBody ViewLogRequest request) {
        String loginId = principal == null ? null : principal.getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(viewLogService.record(loginId, request));
    }
}
