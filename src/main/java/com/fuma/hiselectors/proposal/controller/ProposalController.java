package com.fuma.hiselectors.proposal.controller;

import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.dto.ProposalHistoryResponse;
import com.fuma.hiselectors.proposal.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "크리에이터 제안", description = "관리자용 크리에이터 제안 이력 조회·발송")
@RestController
@RequestMapping("/api/admin/proposals")
@RequiredArgsConstructor
@Validated
public class ProposalController {

    private final ProposalService proposalService;

    @Operation(summary = "제안 이력 목록 조회",
            description = "proposal_history + creator_pool + admin 을 조인해 최신순으로 반환한다.")
    @GetMapping
    public ResponseEntity<Page<ProposalHistoryResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(proposalService.list(PageRequest.of(page, size)));
    }

    @Operation(summary = "제안 메일 발송",
            description = "크리에이터에게 제안 메일을 보내고 proposal_history 에 이력을 남긴다.")
    @PostMapping
    public ResponseEntity<ProposalHistoryResponse> propose(
            Principal principal,
            @Valid @RequestBody ProposalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(proposalService.propose(principal.getName(), request));
    }
}
