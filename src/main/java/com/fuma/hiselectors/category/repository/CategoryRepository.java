package com.fuma.hiselectors.category.repository;

import com.fuma.hiselectors.category.model.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCode(String code);

    boolean existsByName(String name);

    /** 이름 수정 시 자기 자신은 중복 검사에서 빼야 한다. */
    boolean existsByNameAndIdNot(String name, Long id);

    /** creator_pool.category 에 저장된 코드로 역조회할 때 쓴다. */
    Optional<Category> findByCode(String code);

    /** 기본 카테고리 초기화 시 동일 이름의 다른 코드가 있는지 확인한다. */
    Optional<Category> findByName(String name);

    /** 목록 조회는 키워드까지 같이 쓰므로 N+1 을 피한다. */
    @EntityGraph(attributePaths = "keywords")
    List<Category> findAllByOrderByDisplayOrderAscIdAsc();

    @EntityGraph(attributePaths = "keywords")
    List<Category> findByEnabledTrueOrderByDisplayOrderAscIdAsc();
}
