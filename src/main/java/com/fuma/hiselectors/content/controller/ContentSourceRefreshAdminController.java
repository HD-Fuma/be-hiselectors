package com.fuma.hiselectors.content.controller;

import com.fuma.hiselectors.content.dto.ContentSourceRefreshResponse;
import com.fuma.hiselectors.content.service.ContentSourceRefreshService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/contents")
@RequiredArgsConstructor
@Tag(name = "콘텐츠 검수", description = "현재 활동 기수 콘텐츠 검수 조회 (관리자 전용)")
public class ContentSourceRefreshAdminController {

    private final ContentSourceRefreshService contentSourceRefreshService;

    @Operation(summary = "기존 콘텐츠 원본 메타·성과 갱신",
            description = "콘텐츠 배치가 계정 피드로 수집하지 못한 DB 저장 콘텐츠를 "
                    + "content_url·sns_content_id로 SNS API에서 다시 조회한다. "
                    + "작성자 프로필 이미지, 제목·본문, 조회수·좋아요·댓글을 최신 버전에 채운다. "
                    + "contentId가 없으면 현재 기수에서 본문 또는 성과가 비어 있는 콘텐츠만 고른다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "갱신 완료"),
            @ApiResponse(responseCode = "404", description = "콘텐츠 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "현재 활동 기수 없음", content = @Content)
    })
    @PostMapping("/source-refresh")
    public ResponseEntity<ContentSourceRefreshResponse> refresh(
            @Parameter(description = "지정하면 해당 콘텐츠만 갱신한다")
            @RequestParam(required = false) Long contentId) {
        return ResponseEntity.ok(contentSourceRefreshService.refresh(contentId));
    }
}
