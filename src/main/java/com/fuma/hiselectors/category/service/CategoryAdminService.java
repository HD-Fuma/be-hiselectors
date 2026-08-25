package com.fuma.hiselectors.category.service;

import com.fuma.hiselectors.category.dto.CategoryCreateRequest;
import com.fuma.hiselectors.category.dto.CategoryResponse;
import com.fuma.hiselectors.category.dto.CategoryUpdateRequest;
import com.fuma.hiselectors.category.dto.KeywordCreateRequest;
import com.fuma.hiselectors.category.dto.KeywordCreateResponse;
import com.fuma.hiselectors.category.dto.KeywordResponse;
import com.fuma.hiselectors.category.dto.KeywordUpdateRequest;
import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발굴 카테고리·키워드 관리 (관리자 전용).
 *
 * <p>여기서 등록한 키워드가 그대로 YouTube 검색어가 되므로 발굴 품질은
 * 이 기능에서 결정된다. 개발자 배포 없이 관리자가 바꿀 수 있어야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryAdminService {

    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_LENGTH = 30;

    private final CategoryRepository categoryRepository;
    private final DiscoveryKeywordRepository keywordRepository;
    private final CreatorDiscoverySourceRepository discoverySourceRepository;

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse findOne(Long categoryId) {
        return CategoryResponse.from(getCategory(categoryId));
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String code = request.code().trim();
        String name = request.name().trim();

        if (categoryRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.CATEGORY_CODE_DUPLICATED,
                    "이미 존재하는 카테고리 코드입니다: " + code);
        }
        if (categoryRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED,
                    "이미 존재하는 카테고리명입니다: " + name);
        }

        Category created = categoryRepository.save(Category.builder()
                .code(code)
                .name(name)
                .displayOrder(request.displayOrder())
                .build());

        return CategoryResponse.from(created);
    }

    @Transactional
    public CategoryResponse update(Long categoryId, CategoryUpdateRequest request) {
        Category category = getCategory(categoryId);

        String name = request.name() == null ? null : request.name().trim();
        if (name != null && categoryRepository.existsByNameAndIdNot(name, categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_DUPLICATED,
                    "이미 존재하는 카테고리명입니다: " + name);
        }

        category.update(name, request.displayOrder(), request.enabled());
        return CategoryResponse.from(category);
    }

    /**
     * 하위 키워드도 함께 삭제된다 (cascade + orphanRemoval).
     *
     * <p>단, 이 카테고리의 키워드로 발굴된 이력이 있으면 삭제할 수 없다.
     * {@code creator_discovery_source} 가 키워드를 참조하고 있어 DB 제약에 걸리고,
     * 무엇보다 발굴 출처를 잃으면 대표 카테고리를 다시 계산할 수 없게 된다.
     * 발굴에서만 빼려면 {@code enabled = false} 로 비활성화한다.
     */
    @Transactional
    public void delete(Long categoryId) {
        Category category = getCategory(categoryId);

        if (discoverySourceRepository.existsByKeywordCategoryId(categoryId)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE,
                    "'" + category.getName() + "' 카테고리의 키워드로 발굴된 이력이 있어 삭제할 수 없습니다. "
                            + "발굴 대상에서 빼려면 비활성화하세요.");
        }
        categoryRepository.delete(category);
    }

    @Transactional
    public KeywordCreateResponse addKeyword(Long categoryId, KeywordCreateRequest request) {
        Category category = getCategory(categoryId);
        String keyword = request.keyword() == null ? "" : request.keyword().trim();
        if (keyword.length() < MIN_KEYWORD_LENGTH || keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "키워드는 2자 이상 30자 이하여야 합니다.");
        }

        if (category.hasKeyword(keyword)) {
            throw new BusinessException(ErrorCode.KEYWORD_DUPLICATED,
                    "이미 등록된 키워드입니다: " + keyword);
        }

        DiscoveryKeyword created = category.addKeyword(keyword, request.priority());
        categoryRepository.flush();   // 아래 경고 조회 전에 ID 를 확정한다

        return KeywordCreateResponse.of(created, buildWarnings(keyword, categoryId));
    }

    @Transactional
    public KeywordResponse updateKeyword(
            Long categoryId, Long keywordId, KeywordUpdateRequest request) {
        DiscoveryKeyword keyword = getKeyword(categoryId, keywordId);
        keyword.update(request.enabled(), request.priority());
        return KeywordResponse.from(keyword);
    }

    /** 이 키워드로 발굴된 이력이 있으면 삭제할 수 없다. 비활성화를 사용한다. */
    @Transactional
    public void removeKeyword(Long categoryId, Long keywordId) {
        Category category = getCategory(categoryId);
        DiscoveryKeyword keyword = getKeyword(categoryId, keywordId);

        if (discoverySourceRepository.existsByKeywordId(keywordId)) {
            throw new BusinessException(ErrorCode.KEYWORD_IN_USE,
                    "'" + keyword.getKeyword() + "' 키워드로 발굴된 이력이 있어 삭제할 수 없습니다. "
                            + "발굴 대상에서 빼려면 비활성화하세요.");
        }
        category.removeKeyword(keyword);
    }

    /**
     * 막지 않고 알려주기만 하는 경고.
     *
     * <p>'하울' 이 뷰티와 패션 양쪽에 있는 건 정상이다. 다만 발굴된 채널의
     * 대표 카테고리 판정에 영향이 있으므로 관리자가 알고 있어야 한다.
     */
    private List<String> buildWarnings(String keyword, Long categoryId) {
        List<String> warnings = new ArrayList<>();

        List<DiscoveryKeyword> others =
                keywordRepository.findSameKeywordInOtherCategories(keyword, categoryId);
        if (!others.isEmpty()) {
            String names = others.stream()
                    .map(k -> k.getCategory().getName())
                    .distinct()
                    .collect(Collectors.joining(", "));
            warnings.add("같은 키워드가 다른 카테고리에도 있습니다: " + names
                    + ". 발굴은 정상 동작하지만 대표 카테고리 판정에 영향이 있습니다.");
        }
        return warnings;
    }

    private Category getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 다른 카테고리의 키워드를 건드리지 못하도록 소속까지 확인한다. */
    private DiscoveryKeyword getKeyword(Long categoryId, Long keywordId) {
        DiscoveryKeyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));

        if (!keyword.getCategory().getId().equals(categoryId)) {
            throw new BusinessException(ErrorCode.KEYWORD_NOT_FOUND,
                    "해당 카테고리의 키워드가 아닙니다.");
        }
        return keyword;
    }
}
