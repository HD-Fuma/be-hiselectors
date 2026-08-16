package com.fuma.hiselectors.selectors.repository;

import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SelectorsRepository extends JpaRepository<Selectors, Long> {

    Optional<Selectors> findBySelectorsCode(String selectorsCode);

    Optional<Selectors> findByUserId(Long userId);

    @Query("select s.id from Selectors s order by s.id")
    List<Long> findAllIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Selectors s where s.id = :selectorsId")
    Optional<Selectors> findByIdForUpdate(@Param("selectorsId") Long selectorsId);
}
