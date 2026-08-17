package com.fuma.hiselectors.campaign.controller;

import com.fuma.hiselectors.campaign.dto.CampaignCreateRequest;
import com.fuma.hiselectors.campaign.dto.CampaignParticipantResponse;
import com.fuma.hiselectors.campaign.dto.CampaignResponse;
import com.fuma.hiselectors.campaign.dto.CampaignUpdateRequest;
import com.fuma.hiselectors.campaign.service.CampaignAdminService;
import com.fuma.hiselectors.campaign.service.CampaignParticipantService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
public class CampaignAdminController {

    private final CampaignAdminService campaignAdminService;
    private final CampaignParticipantService campaignParticipantService;

    @GetMapping
    public ResponseEntity<Page<CampaignResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(campaignAdminService.search(keyword, startDate, endDate, pageable));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> findOne(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignAdminService.findOne(campaignId));
    }

    @GetMapping("/{campaignId}/participants")
    public ResponseEntity<Page<CampaignParticipantResponse>> findParticipants(@PathVariable Long campaignId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(campaignParticipantService.findParticipants(campaignId, pageable));
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignAdminService.create(request));
    }

    @PatchMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> update(@PathVariable Long campaignId,
                                                    @Valid @RequestBody CampaignUpdateRequest request) {
        return ResponseEntity.ok(campaignAdminService.update(campaignId, request));
    }

    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> delete(@PathVariable Long campaignId) {
        campaignAdminService.delete(campaignId);
        return ResponseEntity.noContent().build();
    }
}
