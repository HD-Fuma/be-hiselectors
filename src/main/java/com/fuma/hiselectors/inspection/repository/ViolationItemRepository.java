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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select vi from ViolationItem vi, ContentVersion cv
            where vi.contentVersionId = cv.id
              and cv.contentId = :contentId
              and vi.status in :statuses
            """)
    List<ViolationItem> findOpenByContentIdForUpdate(
            @Param("contentId") Long contentId,
            @Param("statuses") Collection<ViolationStatus> statuses);

    @Query("""
            select case when count(vi) > 0 then true else false end
            from ViolationItem vi, ContentVersion cv, Content c
            where vi.contentVersionId = cv.id
              and cv.contentId = c.id
              and c.selectorsId = :selectorsId
              and vi.status in :statuses
            """)
    boolean existsOpenBySelectorsId(
            @Param("selectorsId") Long selectorsId,
            @Param("statuses") Collection<ViolationStatus> statuses);
}
