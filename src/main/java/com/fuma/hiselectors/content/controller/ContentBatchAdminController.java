package com.fuma.hiselectors.content.controller;

import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content-batch")
@RequiredArgsConstructor
public class ContentBatchAdminController {

    private final ContentBatchService contentBatchService;

    /** 콘텐츠 배치를 수동으로 실행합니다. */
    @PostMapping("/run")
    public ResponseEntity<ContentBatchResult> run() {
        return ResponseEntity.ok(contentBatchService.run());
    }
}
