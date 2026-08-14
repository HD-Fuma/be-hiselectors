package com.fuma.hiselectors.category.bootstrap;

import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryCatalog.DefaultCategory;
import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryCatalog.DefaultKeyword;
import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 하나의 기본값을 독립된 트랜잭션에서 저장한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDiscoveryCategoryWriter {

    private final CategoryRepository categoryRepository;

    /**
     * 카테고리별 트랜잭션을 분리하여 동시 초기화 충돌이 전체 초기화를 롤백시키지 않게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CategoryInitializationResult initialize(DefaultCategory defaultCategory) {
        Optional<Category> foundByCode = categoryRepository.findByCode(defaultCategory.code());

        if (foundByCode.isPresent()) {
            Category existing = foundByCode.get();
            if (!existing.isEnabled() || !existing.getKeywords().isEmpty()) {
                return CategoryInitializationResult.skipped();
            }

            int createdKeywords = addDefaultKeywords(existing, defaultCategory);
            categoryRepository.saveAndFlush(existing);
            return CategoryInitializationResult.keywordsCreated(createdKeywords);
        }

        Optional<Category> sameName = categoryRepository.findByName(defaultCategory.name());
        if (sameName.isPresent()) {
            log.warn("기본 발굴 카테고리 '{}'와 같은 이름이 코드 '{}'로 이미 존재하여 건너뜁니다.",
                    defaultCategory.name(), sameName.get().getCode());
            return CategoryInitializationResult.skipped();
        }

        Category created = Category.builder()
                .code(defaultCategory.code())
                .name(defaultCategory.name())
                .displayOrder(defaultCategory.displayOrder())
                .enabled(true)
                .build();
        int createdKeywords = addDefaultKeywords(created, defaultCategory);
        categoryRepository.saveAndFlush(created);
        return CategoryInitializationResult.categoryCreated(createdKeywords);
    }

    private int addDefaultKeywords(Category category, DefaultCategory defaultCategory) {
        for (DefaultKeyword keyword : defaultCategory.keywords()) {
            category.addKeyword(keyword.keyword(), keyword.priority());
        }
        return defaultCategory.keywords().size();
    }

    public record CategoryInitializationResult(
            int createdCategories,
            int createdKeywords,
            int skippedCategories
    ) {
        static CategoryInitializationResult categoryCreated(int keywordCount) {
            return new CategoryInitializationResult(1, keywordCount, 0);
        }

        static CategoryInitializationResult keywordsCreated(int keywordCount) {
            return new CategoryInitializationResult(0, keywordCount, 0);
        }

        static CategoryInitializationResult skipped() {
            return new CategoryInitializationResult(0, 0, 1);
        }
    }
}
