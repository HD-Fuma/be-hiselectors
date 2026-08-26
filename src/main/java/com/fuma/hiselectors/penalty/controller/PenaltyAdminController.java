package com.fuma.hiselectors.penalty.controller;

import com.fuma.hiselectors.penalty.dto.PenaltyCreateRequest;
import com.fuma.hiselectors.penalty.service.PenaltyService;
import com.fuma.hiselectors.selectors.dto.PenaltyHistoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/selectors/{selectorsId}/penalties")
@Tag(name = "셀렉터스 패널티", description = "셀렉터스 패널티 부여 및 수동 해제 (관리자 전용)")
public class PenaltyAdminController {

    private final PenaltyService penaltyService;

    @Operation(summary = "패널티 부여")
    @PostMapping
    public ResponseEntity<PenaltyHistoryResponse> create(
            @PathVariable Long selectorsId,
            @Valid @RequestBody PenaltyCreateRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(penaltyService.create(selectorsId, request, principal.getName()));
    }

    @Operation(summary = "패널티 수동 해제")
    @PatchMapping("/{penaltyHistoryId}/release")
    public ResponseEntity<PenaltyHistoryResponse> release(
            @PathVariable Long selectorsId,
            @PathVariable Long penaltyHistoryId,
            Principal principal) {
        return ResponseEntity.ok(penaltyService.releaseManually(
                selectorsId, penaltyHistoryId, principal.getName()));
    }
}
