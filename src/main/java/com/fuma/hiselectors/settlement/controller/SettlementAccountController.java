package com.fuma.hiselectors.settlement.controller;

import com.fuma.hiselectors.settlement.dto.SettlementAccountResponse;
import com.fuma.hiselectors.settlement.dto.SettlementAccountUpsertRequest;
import com.fuma.hiselectors.settlement.service.SettlementAccountService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements/account")
@RequiredArgsConstructor
public class SettlementAccountController {

    private final SettlementAccountService settlementAccountService;

    @GetMapping
    public ResponseEntity<SettlementAccountResponse> getAccount(Principal principal) {
        return ResponseEntity.ok(settlementAccountService.getAccount(principal.getName()));
    }

    @PutMapping
    public ResponseEntity<SettlementAccountResponse> upsert(
            Principal principal, @Valid @RequestBody SettlementAccountUpsertRequest request) {
        return ResponseEntity.ok(settlementAccountService.upsert(principal.getName(), request));
    }
}
