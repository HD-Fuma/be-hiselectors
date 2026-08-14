package com.fuma.hiselectors.category.bootstrap;

import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryCatalog.DefaultCategory;
import com.fuma.hiselectors.category.bootstrap.DefaultDiscoveryCategoryWriter.CategoryInitializationResult;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 더현대Hi 기반 기본 발굴 카테고리·키워드를 멱등하게 초기화한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultDiscoveryDataService {

    private final DefaultDiscoveryCategoryWriter categoryWriter;
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
    public InitializationResult initialize() {
        int createdCategories = 0;
        int createdKeywords = 0;
        int skippedCategories = 0;

        for (DefaultCategory defaultCategory : DefaultDiscoveryCatalog.CATEGORIES) {
            try {
                CategoryInitializationResult result = categoryWriter.initialize(defaultCategory);
                createdCategories += result.createdCategories();
                createdKeywords += result.createdKeywords();
                skippedCategories += result.skippedCategories();
            } catch (DataIntegrityViolationException exception) {
                if (!categoryRepository.existsByCode(defaultCategory.code())
                        && !categoryRepository.existsByName(defaultCategory.name())) {
                    throw exception;
                }

                // 다른 애플리케이션 인스턴스가 같은 기본값을 먼저 커밋한 경우다.
                skippedCategories++;
                log.info("기본 발굴 카테고리 '{}'는 다른 인스턴스에서 이미 초기화하여 건너뜁니다.",
                        defaultCategory.name());
            }
        }

        return new InitializationResult(
                createdCategories, createdKeywords, skippedCategories);
    }

    public record InitializationResult(
            int createdCategories,
            int createdKeywords,
            int skippedCategories
    ) {
    }
}
