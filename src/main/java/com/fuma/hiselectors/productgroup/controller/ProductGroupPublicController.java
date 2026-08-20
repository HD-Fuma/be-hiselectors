package com.fuma.hiselectors.productgroup.controller;

import com.fuma.hiselectors.productgroup.dto.ProductGroupResponse;
import com.fuma.hiselectors.productgroup.dto.SelectorsShopResponse;
import com.fuma.hiselectors.productgroup.service.ProductGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "셀렉터스 샵", description = "로그인 없이 셀렉터스 샵과 상품 그룹 조회")
@SecurityRequirements
@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
public class ProductGroupPublicController {

    private final ProductGroupService productGroupService;

    @Operation(summary = "셀렉터스 샵 조회",
            description = "셀렉터스 프로필과 삭제되지 않은 상품 그룹·상품을 한 번에 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "셀렉터스 없음", content = @Content)
    })
    @GetMapping("/{selectorsCode}")
    public ResponseEntity<SelectorsShopResponse> findShop(@PathVariable String selectorsCode) {
        return ResponseEntity.ok(productGroupService.findPublicShop(selectorsCode));
    }

    @Operation(summary = "셀렉터스 샵 상품 그룹 목록 조회",
            description = "기존 클라이언트 호환용 상품 그룹 전용 공개 조회 API다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "셀렉터스 없음", content = @Content)
    })
    @GetMapping("/{selectorsCode}/product-groups")
    public ResponseEntity<List<ProductGroupResponse>> findPublic(@PathVariable String selectorsCode) {
        return ResponseEntity.ok(productGroupService.findPublic(selectorsCode));
    }
}
