package com.fuma.hiselectors.product.controller;

import com.fuma.hiselectors.product.dto.ProductSearchResponse;
import com.fuma.hiselectors.product.model.ProductStatus;
import com.fuma.hiselectors.product.service.ProductAdminService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@Tag(name = "상품 관리", description = "상품 목록 검색 및 상태별 조회 (관리자 전용)")
public class ProductAdminController {

    private final ProductAdminService productAdminService;

    @GetMapping
    public ResponseEntity<Page<ProductSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productAdminService.search(keyword, status, pageable));
    }
}
