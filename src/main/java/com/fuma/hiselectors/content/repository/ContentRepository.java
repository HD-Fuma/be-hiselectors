package com.fuma.hiselectors.content.repository;

import com.fuma.hiselectors.content.model.Content;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Content c where c.id = :id")
    Optional<Content> findByIdForUpdate(@Param("id") Long id);
}
