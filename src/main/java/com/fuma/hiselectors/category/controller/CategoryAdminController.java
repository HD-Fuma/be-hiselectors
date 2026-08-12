package com.fuma.hiselectors.category.controller;

import com.fuma.hiselectors.category.dto.CategoryCreateRequest;
import com.fuma.hiselectors.category.dto.CategoryResponse;
import com.fuma.hiselectors.category.dto.CategoryUpdateRequest;
import com.fuma.hiselectors.category.dto.KeywordCreateRequest;
import com.fuma.hiselectors.category.dto.KeywordCreateResponse;
import com.fuma.hiselectors.category.dto.KeywordResponse;
import com.fuma.hiselectors.category.dto.KeywordUpdateRequest;
import com.fuma.hiselectors.category.service.CategoryAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 발굴 카테고리·키워드 관리 API.
 *
 * <p>{@code /api/admin/**} 은 SecurityConfig 에서 ROLE_ADMIN 으로 제한되어 있다.
 */
@Tag(name = "발굴 카테고리 관리", description = "크리에이터 발굴에 쓸 카테고리와 검색 키워드 관리 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class CategoryAdminController {

    private final CategoryAdminService categoryAdminService;

    @Operation(summary = "카테고리 목록 조회",
            description = "모든 발굴 카테고리를 하위 키워드까지 함께 조회한다.")
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(categoryAdminService.findAll());
    }

    @Operation(summary = "카테고리 단건 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "카테고리 없음", content = @Content)
    })
    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> findOne(@PathVariable Long categoryId) {
        return ResponseEntity.ok(categoryAdminService.findOne(categoryId));
    }

    @Operation(summary = "카테고리 생성",
            description = "카테고리 코드는 creator_pool.category 에 저장되는 값이므로 "
                    + "등록 후 변경할 수 없다. 예) BEAUTY, FASHION")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 검증 실패", content = @Content),
            @ApiResponse(responseCode = "409", description = "코드 또는 이름 중복", content = @Content)
    })
    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryAdminService.create(request));
    }

    @Operation(summary = "카테고리 수정",
            description = "이름·노출순서·활성여부를 수정한다. null 인 필드는 변경하지 않는다. "
                    + "카테고리 코드는 변경할 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "카테고리 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "카테고리명 중복", content = @Content)
    })
    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(categoryAdminService.update(categoryId, request));
    }

    @Operation(summary = "카테고리 삭제", description = "하위 발굴 키워드도 함께 삭제된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "카테고리 없음", content = @Content)
    })
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Long categoryId) {
        categoryAdminService.delete(categoryId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "발굴 키워드 등록",
            description = "이 키워드가 그대로 YouTube 검색어가 된다. "
                    + "다른 카테고리에 같은 키워드가 있으면 등록은 되지만 warnings 로 알려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "404", description = "카테고리 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "같은 카테고리에 이미 있는 키워드", content = @Content)
    })
    @PostMapping("/{categoryId}/keywords")
    public ResponseEntity<KeywordCreateResponse> addKeyword(
            @PathVariable Long categoryId,
            @Valid @RequestBody KeywordCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryAdminService.addKeyword(categoryId, request));
    }

    @Operation(summary = "발굴 키워드 수정",
            description = "활성 여부와 우선순위를 수정한다. 쿼터가 한정적이라 "
                    + "우선순위가 실제 발굴 순서를 결정한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "카테고리 또는 키워드 없음", content = @Content)
    })
    @PatchMapping("/{categoryId}/keywords/{keywordId}")
    public ResponseEntity<KeywordResponse> updateKeyword(
            @PathVariable Long categoryId,
            @PathVariable Long keywordId,
            @Valid @RequestBody KeywordUpdateRequest request) {
        return ResponseEntity.ok(
                categoryAdminService.updateKeyword(categoryId, keywordId, request));
    }

    @Operation(summary = "발굴 키워드 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "카테고리 또는 키워드 없음", content = @Content)
    })
    @DeleteMapping("/{categoryId}/keywords/{keywordId}")
    public ResponseEntity<Void> removeKeyword(
            @PathVariable Long categoryId, @PathVariable Long keywordId) {
        categoryAdminService.removeKeyword(categoryId, keywordId);
        return ResponseEntity.noContent().build();
    }
}
