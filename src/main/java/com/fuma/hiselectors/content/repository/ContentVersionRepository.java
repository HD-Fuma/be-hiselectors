package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.ContentVersion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ContentVersion v where v.id = :id")
    Optional<ContentVersion> findByIdForUpdate(@Param("id") Long id);

    Optional<ContentVersion> findByContentIdAndContentHash(Long contentId, String contentHash);
}
