package com.fuma.hiselectors.product.controller;

import com.fuma.hiselectors.product.dto.ProductSearchResponse;
import com.fuma.hiselectors.product.model.ProductStatus;
import com.fuma.hiselectors.product.service.ProductAdminService;
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
