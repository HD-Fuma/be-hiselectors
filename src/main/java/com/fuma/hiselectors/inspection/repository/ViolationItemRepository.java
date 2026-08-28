package com.fuma.hiselectors.inspection.repository;

import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViolationItemRepository extends JpaRepository<ViolationItem, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vi from ViolationItem vi where vi.id = :id")
    Optional<ViolationItem> findByIdForUpdate(@Param("id") Long id);

    List<ViolationItem> findAllByContentIdAndStatusInOrderByIdAsc(
            Long contentId, Collection<ViolationStatus> statuses);

    List<ViolationItem> findAllByContentIdOrderByIdAsc(Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vi from ViolationItem vi where vi.contentId = :contentId")
    List<ViolationItem> findAllByContentIdForUpdate(@Param("contentId") Long contentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vi from ViolationItem vi where vi.id in :ids order by vi.id")
    List<ViolationItem> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    List<ViolationItem> findAllByResolvedContentVersionIdAndStatusOrderByIdAsc(
            Long contentVersionId, ViolationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select vi
            from ViolationItem vi
            where vi.resolvedContentVersionId = :contentVersionId
              and vi.status = :status
            order by vi.id
            """)
    List<ViolationItem> findAllByResolutionCandidateForUpdate(
            @Param("contentVersionId") Long contentVersionId,
            @Param("status") ViolationStatus status);

    @Query("""
            select case when count(vi) > 0 then true else false end
            from ViolationItem vi, Content c
            where vi.contentId = c.id
              and c.selectorsId = :selectorsId
              and vi.status in :statuses
            """)
    boolean existsOpenBySelectorsId(
            @Param("selectorsId") Long selectorsId,
            @Param("statuses") Collection<ViolationStatus> statuses);
}
