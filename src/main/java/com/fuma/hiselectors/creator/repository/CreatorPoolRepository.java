package com.fuma.hiselectors.creator.repository;

import com.fuma.hiselectors.creator.model.CreatorPool;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorPoolRepository extends JpaRepository<CreatorPool, Long> {

    /**
     * 발굴 파이프라인이 중복 저장을 피하려고 쓴다.
     *
     * <p>소프트 삭제된 행도 함께 찾는다. 걸러내지 않으면 이미 지운 계정이
     * 다시 발굴될 때 같은 (sns_code, account_id) 행이 하나 더 생긴다.
     * 지워진 계정을 되살릴지는 호출부가 {@code isDeleted()} 를 보고 정한다.
     *
     * <p>{@code findFirst} 인 이유: creator_pool 에는 (sns_code, account_id)
     * 유니크 제약이 없다. 기존 테이블이라 제약을 추가할 수 없으므로,
     * 어쩌다 중복 행이 생겨도 예외 대신 한 건만 돌려주게 한다.
     */
    Optional<CreatorPool> findFirstBySnsCodeAndAccountIdOrderByIdAsc(
            String snsCode, String accountId);

    /** 화면 조회용. 소프트 삭제된 계정은 제외한다. */
    Optional<CreatorPool> findFirstBySnsCodeAndAccountIdAndDeletedFalseOrderByIdAsc(
            String snsCode, String accountId);
}
