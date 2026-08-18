package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelectorsSnsAccountRepository
        extends JpaRepository<SelectorsSnsAccount, Long> {

    List<SelectorsSnsAccount> findAllBySelectorsId(Long selectorsId);

    Optional<SelectorsSnsAccount> findFirstBySelectorsIdAndDeletedFalseOrderByLastCollectedAtDescIdDesc(
            Long selectorsId);

    /** 목록 화면의 대표 계정 계산용. 페이지에 걸린 셀렉터스들의 계정을 한 번에 가져온다. */
    List<SelectorsSnsAccount> findAllBySelectorsIdInAndDeletedFalse(List<Long> selectorsIds);

    /** 상세 화면용. 삭제되지 않은 계정만 최신 수집순으로. */
    List<SelectorsSnsAccount> findAllBySelectorsIdAndDeletedFalseOrderByLastCollectedAtDescIdDesc(
            Long selectorsId);
}
