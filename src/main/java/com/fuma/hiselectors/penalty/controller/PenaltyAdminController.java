package com.fuma.hiselectors.penalty.controller;

import com.fuma.hiselectors.penalty.dto.PenaltyCreateRequest;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import com.fuma.hiselectors.selectors.dto.PenaltyHistoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/selectors/{selectorsId}/penalties")
public class PenaltyAdminController {

    private final PenaltyService penaltyService;

    @PostMapping
    public ResponseEntity<PenaltyHistoryResponse> create(
            @PathVariable Long selectorsId,
            @Valid @RequestBody PenaltyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(penaltyService.create(selectorsId, request));
    }
}
