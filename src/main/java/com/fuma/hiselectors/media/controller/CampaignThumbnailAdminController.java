package com.fuma.hiselectors.media.controller;

import com.fuma.hiselectors.media.dto.CampaignThumbnailUploadResponse;
import com.fuma.hiselectors.media.service.CampaignThumbnailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "관리자 미디어", description = "관리자 전용 미디어 파일 업로드")
@RestController
@RequestMapping("/api/admin/uploads")
@RequiredArgsConstructor
public class CampaignThumbnailAdminController {

    private final CampaignThumbnailService campaignThumbnailService;

    @Operation(summary = "캠페인 썸네일 업로드")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "파일 형식 또는 크기 오류", content = @Content),
            @ApiResponse(responseCode = "502", description = "S3 업로드 실패", content = @Content)
    })
    @PostMapping(value = "/campaign-thumbnails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CampaignThumbnailUploadResponse> uploadCampaignThumbnail(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignThumbnailService.upload(file));
    }
}
