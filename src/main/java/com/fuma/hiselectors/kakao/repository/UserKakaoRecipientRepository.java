package com.fuma.hiselectors.kakao.repository;

import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserKakaoRecipientRepository extends JpaRepository<UserKakaoRecipient, Long> {

    Optional<UserKakaoRecipient> findByUserId(Long userId);

    Optional<UserKakaoRecipient> findByKakaoUserId(Long kakaoUserId);

    Optional<UserKakaoRecipient> findByKakaoMessageUuid(String kakaoMessageUuid);

    boolean existsByKakaoUserId(Long kakaoUserId);

    boolean existsByKakaoMessageUuid(String kakaoMessageUuid);
}
