package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelectorsRepository extends JpaRepository<Selectors, Long> {

    Optional<Selectors> findBySelectorsCode(String selectorsCode);

    Optional<Selectors> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Selectors s where s.userId = :userId")
    Optional<Selectors> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select s.id from Selectors s order by s.id")
    List<Long> findAllIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Selectors s where s.id = :selectorsId")
    Optional<Selectors> findByIdForUpdate(@Param("selectorsId") Long selectorsId);

    /** 관리자 상세 조회용. 탈퇴·제명 처리된 셀렉터스는 없는 것으로 본다. */
    Optional<Selectors> findByIdAndDeletedFalse(Long id);

    /**
     * 관리자 목록 조회. 조건이 null 이면 그 조건은 적용하지 않는다.
     *
     * <p>기수·SNS 조건은 exists 로 건다.
     */
    @Query(value = """
            select s from Selectors s
            where s.deleted = false
              and (:roleId is null or s.selectorsRoleId = :roleId)
              and (:nickname is null
                   or s.selectorsNickname like concat('%', :nickname, '%'))
              and (:generationId is null or exists (
                    select 1 from SelectorsGeneration sg
                    where sg.selectorsId = s.id and sg.generationId = :generationId))
              and (:snsCode is null or exists (
                    select 1 from SelectorsSnsAccount a
                    where a.selectorsId = s.id
                      and a.deleted = false and a.snsCode = :snsCode))
            """,
            countQuery = """
            select count(s) from Selectors s
            where s.deleted = false
              and (:roleId is null or s.selectorsRoleId = :roleId)
              and (:nickname is null
                   or s.selectorsNickname like concat('%', :nickname, '%'))
              and (:generationId is null or exists (
                    select 1 from SelectorsGeneration sg
                    where sg.selectorsId = s.id and sg.generationId = :generationId))
              and (:snsCode is null or exists (
                    select 1 from SelectorsSnsAccount a
                    where a.selectorsId = s.id
                      and a.deleted = false and a.snsCode = :snsCode))
            """)
    Page<Selectors> search(@Param("roleId") String roleId,
                           @Param("generationId") Long generationId,
                           @Param("nickname") String nickname,
                           @Param("snsCode") SnsPlatform snsCode,
                           Pageable pageable);

    @Query(value = """
            select s from Selectors s
            where s.deleted = false
              and ((:blacklistOnly = true and s.selectorsRoleId = 'BLACKLIST')
                   or (:blacklistOnly = false and exists (
                        select 1 from PenaltyHistory p
                        where p.selectorsId = s.id
                          and (:generationId is null or p.generationId = :generationId)
                          and (:status is null or p.status = :status))))
              and (:generationId is null or exists (
                    select 1 from SelectorsGeneration sg
                    where sg.selectorsId = s.id and sg.generationId = :generationId))
            """,
            countQuery = """
            select count(s) from Selectors s
            where s.deleted = false
              and ((:blacklistOnly = true and s.selectorsRoleId = 'BLACKLIST')
                   or (:blacklistOnly = false and exists (
                        select 1 from PenaltyHistory p
                        where p.selectorsId = s.id
                          and (:generationId is null or p.generationId = :generationId)
                          and (:status is null or p.status = :status))))
              and (:generationId is null or exists (
                    select 1 from SelectorsGeneration sg
                    where sg.selectorsId = s.id and sg.generationId = :generationId))
            """)
    Page<Selectors> searchWithPenalties(
            @Param("generationId") Long generationId,
            @Param("status") PenaltyStatus status,
            @Param("blacklistOnly") boolean blacklistOnly,
            Pageable pageable);
}
