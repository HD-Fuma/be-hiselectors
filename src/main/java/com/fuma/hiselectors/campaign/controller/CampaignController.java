package com.fuma.hiselectors.campaign.controller;

import com.fuma.hiselectors.campaign.dto.CampaignDetailResponse;
import com.fuma.hiselectors.campaign.dto.CampaignListResponse;
import com.fuma.hiselectors.campaign.service.CampaignQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignQueryService campaignQueryService;

    @GetMapping
    public ResponseEntity<List<CampaignListResponse>> findAll() {
        return ResponseEntity.ok(campaignQueryService.findAll());
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetailResponse> findOne(@PathVariable Long campaignId) {
        return ResponseEntity.ok(campaignQueryService.findOne(campaignId));
    }
}
