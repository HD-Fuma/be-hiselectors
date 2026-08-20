package com.fuma.hiselectors.productgroup.controller;

import com.fuma.hiselectors.productgroup.dto.ProductGroupItemAddRequest;
import com.fuma.hiselectors.productgroup.dto.MySelectorsShopResponse;
import com.fuma.hiselectors.productgroup.dto.ProductGroupResponse;
import com.fuma.hiselectors.productgroup.dto.ProductGroupSaveRequest;
import com.fuma.hiselectors.productgroup.service.ProductGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "셀렉터스 샵 관리", description = "내 셀렉터스 샵 상품 그룹 생성·조회·수정·삭제")
@RestController
@RequestMapping("/api/product-groups")
@RequiredArgsConstructor
public class ProductGroupController {

    private final ProductGroupService productGroupService;

    @Operation(summary = "내 상품 그룹 목록 조회",
            description = "로그인한 셀렉터스의 삭제되지 않은 상품 그룹과 상품을 노출 순서대로 조회한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "404", description = "셀렉터스 없음", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<List<ProductGroupResponse>> findMine(Principal principal) {
        return ResponseEntity.ok(productGroupService.findMine(principal.getName()));
    }

    @Operation(summary = "내 셀렉터스 샵 조회",
            description = "로그인한 셀렉터스의 코드·닉네임·기수·실명·대표 SNS 정보와 상품 그룹을 한 번에 조회한다. 실명은 공개 샵 API에 포함되지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "404", description = "셀렉터스 없음", content = @Content)
    })
    @GetMapping("/me/shop")
    public ResponseEntity<MySelectorsShopResponse> findMyShop(Principal principal) {
        return ResponseEntity.ok(productGroupService.findMineShop(principal.getName()));
    }

    @Operation(summary = "상품 그룹 생성",
            description = "캠페인 하나를 선택하고 해당 캠페인의 상품만 담아 그룹을 생성한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 또는 캠페인-상품 불일치", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "404", description = "셀렉터스 또는 캠페인 없음", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ProductGroupResponse> create(Principal principal,
            @Valid @RequestBody ProductGroupSaveRequest request) {
        ProductGroupResponse response = productGroupService.create(principal.getName(), request);
        return ResponseEntity.created(URI.create("/api/product-groups/" + response.id())).body(response);
    }

    @Operation(summary = "상품 그룹 수정",
            description = "그룹 제목·캠페인·상품 구성을 변경한다. 삭제했던 동일 상품은 기존 항목을 복구한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 또는 캠페인-상품 불일치", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "404", description = "상품 그룹 또는 캠페인 없음", content = @Content)
    })
    @PutMapping("/{groupId}")
    public ResponseEntity<ProductGroupResponse> update(Principal principal, @PathVariable Long groupId,
            @Valid @RequestBody ProductGroupSaveRequest request) {
        return ResponseEntity.ok(productGroupService.update(principal.getName(), groupId, request));
    }

    @Operation(summary = "상품 그룹에 상품 추가",
            description = "그룹에 연결된 캠페인의 상품만 추가하며 삭제했던 동일 상품은 복구한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추가 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 또는 캠페인-상품 불일치", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "404", description = "상품 그룹 없음", content = @Content)
    })
    @PostMapping("/{groupId}/items")
    public ResponseEntity<ProductGroupResponse> addItems(Principal principal, @PathVariable Long groupId,
            @Valid @RequestBody ProductGroupItemAddRequest request) {
        return ResponseEntity.ok(productGroupService.addItems(principal.getName(), groupId, request));
    }

    @Operation(summary = "상품 그룹 삭제", description = "상품 그룹과 소속 항목을 soft delete 한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "404", description = "상품 그룹 없음", content = @Content)
    })
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long groupId) {
        productGroupService.delete(principal.getName(), groupId);
        return ResponseEntity.noContent().build();
    }
}
