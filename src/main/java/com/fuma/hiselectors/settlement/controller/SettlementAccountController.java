package com.fuma.hiselectors.settlement.controller;

import com.fuma.hiselectors.settlement.dto.SettlementAccountResponse;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.service.SettlementAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "정산 정보 관리", description = "정산 정보 조회 및 입력")
@RestController
@RequestMapping("/api/settlements/account")
@RequiredArgsConstructor
public class SettlementAccountController {

    private final SettlementAccountService settlementAccountService;

    @Operation(summary = "정산 정보 조회",
            description = "정산 정보가 등록되어 있다면 조회한다.")
    @GetMapping
    public ResponseEntity<SettlementAccountResponse> getAccount(Principal principal) {
        return ResponseEntity.ok(settlementAccountService.getAccount(principal.getName()));
    }

    @Operation(summary = "정산 정보 입력",
            description = "정산 정보를 입력한다.")
    @PutMapping
    public ResponseEntity<SettlementAccountResponse> upsert(
            Principal principal, @Valid @RequestBody SettlementAccountUpsertRequest request) {
        return ResponseEntity.ok(settlementAccountService.upsert(principal.getName(), request));
    }
}
