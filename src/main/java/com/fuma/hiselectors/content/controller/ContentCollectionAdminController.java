package com.fuma.hiselectors.content.controller;

import com.fuma.hiselectors.content.dto.ContentCollectionBatchResponse;
import com.fuma.hiselectors.content.service.ContentCollectionBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 활성 기수 콘텐츠 수집을 수동으로 실행하는 관리자 API. */
@Tag(name = "콘텐츠 수집", description = "활성 기수 콘텐츠 일괄 수집 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/content-collections")
@RequiredArgsConstructor
public class ContentCollectionAdminController {

    private final ContentCollectionBatchService batchService;

    @Operation(summary = "활성 기수 콘텐츠 일괄 수집",
            description = "활성 기수의 셀렉터스 SNS 계정을 순서대로 한 번씩 수집한다. "
                    + "일부 계정이 실패해도 나머지 계정은 계속 실행한다.")
    @PostMapping
    public ResponseEntity<ContentCollectionBatchResponse> collect() {
        return ResponseEntity.ok(batchService.run());
    }
}
