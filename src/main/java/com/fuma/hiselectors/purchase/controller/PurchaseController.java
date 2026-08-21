package com.fuma.hiselectors.purchase.controller;

import com.fuma.hiselectors.purchase.dto.AuthenticatedPurchaseRequest;
import com.fuma.hiselectors.purchase.dto.PurchaseRequest;
import com.fuma.hiselectors.purchase.dto.PurchaseResponse;
import com.fuma.hiselectors.purchase.model.PurchaseProcessingResult;
import com.fuma.hiselectors.purchase.service.PurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "구매", description = "더현대 Hi 상품 구매")
@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @Operation(summary = "상품 구매")
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<PurchaseResponse> purchase(
            @Valid @RequestBody PurchaseRequest request) {
        PurchaseResponse response = purchaseService.purchase(request);
        HttpStatus status = response.processingResult() == PurchaseProcessingResult.CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @Operation(summary = "로그인 회원 상품 구매")
    @PostMapping("/me")
    public ResponseEntity<PurchaseResponse> purchaseMine(
            Principal principal, @Valid @RequestBody AuthenticatedPurchaseRequest request) {
        PurchaseResponse response = purchaseService.purchase(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
