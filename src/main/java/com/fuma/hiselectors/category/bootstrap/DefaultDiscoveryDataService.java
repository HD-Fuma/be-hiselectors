package com.fuma.hiselectors.category.bootstrap;

import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryCatalog.DefaultCategory;
import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryCatalog.DefaultKeyword;
import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 더현대Hi 기반 기본 발굴 카테고리·키워드를 멱등하게 초기화한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDiscoveryDataService {

    private final CategoryRepository categoryRepository;

    /**
     * 관리자 설정을 덮어쓰지 않는 범위에서 기본값을 채운다.
     *
     * <ul>
     *     <li>코드가 없는 카테고리는 기본 키워드와 함께 생성한다.</li>
     *     <li>활성 카테고리에 키워드가 하나도 없으면 기본 키워드를 채운다.</li>
     *     <li>키워드가 하나라도 있거나 카테고리가 비활성화되어 있으면 유지한다.</li>
     *     <li>동일 이름이 다른 코드로 존재하면 관리자 데이터를 존중하고 건너뛴다.</li>
     * </ul>
     */
    @Transactional
    public InitializationResult initialize() {
        int createdCategories = 0;
        int createdKeywords = 0;
        int skippedCategories = 0;

        for (DefaultCategory defaultCategory : DefaultDiscoveryCatalog.CATEGORIES) {
            Optional<Category> foundByCode = categoryRepository.findByCode(defaultCategory.code());

            if (foundByCode.isPresent()) {
                Category existing = foundByCode.get();
                if (!existing.isEnabled() || !existing.getKeywords().isEmpty()) {
                    skippedCategories++;
                    continue;
                }

                createdKeywords += addDefaultKeywords(existing, defaultCategory);
                categoryRepository.save(existing);
                continue;
            }

            Optional<Category> sameName = categoryRepository.findByName(defaultCategory.name());
            if (sameName.isPresent()) {
                skippedCategories++;
                log.warn("기본 발굴 카테고리 '{}'와 같은 이름이 코드 '{}'로 이미 존재하여 건너뜁니다.",
                        defaultCategory.name(), sameName.get().getCode());
                continue;
            }

            Category created = Category.builder()
                    .code(defaultCategory.code())
                    .name(defaultCategory.name())
                    .displayOrder(defaultCategory.displayOrder())
                    .enabled(true)
                    .build();
            createdKeywords += addDefaultKeywords(created, defaultCategory);
            categoryRepository.save(created);
            createdCategories++;
        }

        return new InitializationResult(
                createdCategories, createdKeywords, skippedCategories);
    }

    private int addDefaultKeywords(Category category, DefaultCategory defaultCategory) {
        for (DefaultKeyword keyword : defaultCategory.keywords()) {
            category.addKeyword(keyword.keyword(), keyword.priority());
        }
        return defaultCategory.keywords().size();
    }

    public record InitializationResult(
            int createdCategories,
            int createdKeywords,
            int skippedCategories
    ) {
    }
}
