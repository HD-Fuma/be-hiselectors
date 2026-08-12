package com.fuma.hiselectors.category.repository;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DiscoveryKeywordRepository extends JpaRepository<DiscoveryKeyword, Long> {

    /**
     * 같은 키워드를 쓰는 다른 카테고리를 찾는다.
     *
     * <p>막지는 않는다. '하울' 이 뷰티와 패션 양쪽에 있는 건 정상이다.
     * 다만 발굴 결과의 카테고리 판정에 영향이 있어 관리자에게 알려준다.
     */
    @Query("""
            select k from DiscoveryKeyword k
            join fetch k.category c
            where lower(k.keyword) = lower(:keyword)
              and c.id <> :excludeCategoryId
            """)
    List<DiscoveryKeyword> findSameKeywordInOtherCategories(String keyword, Long excludeCategoryId);

    /**
     * 발굴 배치가 쓸 목록. 활성 키워드를 우선순위 높은 순,
     * 그다음 오래 안 돌린 순으로 준다. (lastRunAt 이 null 이면 가장 먼저)
     */
    @Query("""
            select k from DiscoveryKeyword k
            join fetch k.category c
            where k.enabled = true and c.enabled = true
            order by k.priority desc, k.lastRunAt asc nulls first
            """)
    List<DiscoveryKeyword> findRunnable();
}