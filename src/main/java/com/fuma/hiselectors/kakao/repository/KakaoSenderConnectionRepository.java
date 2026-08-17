package com.fuma.hiselectors.kakao.repository;

import com.fuma.hiselectors.kakao.model.KakaoSenderConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoSenderConnectionRepository extends JpaRepository<KakaoSenderConnection, Long> {

    Optional<KakaoSenderConnection> findByKakaoUserId(Long kakaoUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KakaoSenderConnection k where k.id = :id")
    Optional<KakaoSenderConnection> findByIdForUpdate(@Param("id") Long id);
}
