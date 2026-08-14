package com.fuma.hiselectors.kakao.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.kakao.model.KakaoSenderConnection;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class KakaoConnectionRepositoryTest {

    @Autowired
    private KakaoSenderConnectionRepository senderRepository;

    @Autowired
    private UserKakaoRecipientRepository recipientRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("여러 관리자가 같은 카카오 발신 계정을 선택할 수 있다")
    void adminsCanShareSenderConnection() {
        KakaoSenderConnection connection = senderRepository.save(senderConnection(100L));
        Admin firstAdmin = admin("admin-1", null);
        Admin secondAdmin = admin("admin-2", null);
        firstAdmin.selectKakaoSenderConnection(connection.getId());
        secondAdmin.selectKakaoSenderConnection(connection.getId());
        Admin first = adminRepository.save(firstAdmin);
        Admin second = adminRepository.save(secondAdmin);
        em.flush();
        em.clear();

        Admin foundFirst = adminRepository.findById(first.getId()).orElseThrow();
        assertThat(foundFirst.getKakaoSenderConnectionId()).isEqualTo(connection.getId());
        assertThat(adminRepository.findById(second.getId()).orElseThrow()
                .getKakaoSenderConnectionId()).isEqualTo(connection.getId());

        foundFirst.clearKakaoSenderConnection();
        em.flush();
        em.clear();
        assertThat(adminRepository.findById(first.getId()).orElseThrow()
                .getKakaoSenderConnectionId()).isNull();
    }

    @Test
    @DisplayName("발신 계정의 카카오 회원번호는 중복될 수 없다")
    void senderKakaoUserIdIsUnique() {
        senderRepository.save(senderConnection(100L));
        em.flush();

        assertThatThrownBy(() -> {
            senderRepository.save(senderConnection(100L));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("사용자별 카카오 수신 연결은 하나만 저장한다")
    void recipientIsUniquePerUser() {
        Long userId = saveUser("user-1").getId();
        recipientRepository.save(recipient(userId, 201L, "uuid-1"));
        em.flush();

        assertThatThrownBy(() -> {
            recipientRepository.save(recipient(userId, 202L, "uuid-2"));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("카카오 회원번호는 수신 연결 간 중복될 수 없다")
    void kakaoUserIdIsUnique() {
        Long firstUserId = saveUser("user-1").getId();
        Long secondUserId = saveUser("user-2").getId();
        recipientRepository.save(recipient(firstUserId, 201L, "uuid-1"));
        em.flush();

        assertThatThrownBy(() -> {
            recipientRepository.save(recipient(secondUserId, 201L, "uuid-2"));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("친구 메시지 UUID는 수신 연결 간 중복될 수 없다")
    void kakaoMessageUuidIsUnique() {
        Long firstUserId = saveUser("user-1").getId();
        Long secondUserId = saveUser("user-2").getId();
        recipientRepository.save(recipient(firstUserId, 201L, "same-uuid"));
        em.flush();

        assertThatThrownBy(() -> {
            recipientRepository.save(recipient(secondUserId, 202L, "same-uuid"));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("수신 연결을 사용자 ID로 조회하고 중복 여부를 확인한다")
    void findRecipientAndCheckDuplicates() {
        Long userId = saveUser("user-1").getId();
        recipientRepository.save(recipient(userId, 201L, "uuid-1"));
        em.flush();
        em.clear();

        assertThat(recipientRepository.findByUserId(userId)).isPresent();
        assertThat(recipientRepository.existsByKakaoUserId(201L)).isTrue();
        assertThat(recipientRepository.existsByKakaoMessageUuid("uuid-1")).isTrue();
    }

    private KakaoSenderConnection senderConnection(Long kakaoUserId) {
        LocalDateTime now = LocalDateTime.now();
        return KakaoSenderConnection.builder()
                .kakaoUserId(kakaoUserId)
                .senderName("공용 발신 계정")
                .accessTokenEncrypted("encrypted-access")
                .refreshTokenEncrypted("encrypted-refresh")
                .accessTokenExpiresAt(now.plusHours(6))
                .refreshTokenExpiresAt(now.plusDays(30))
                .build();
    }

    private Admin admin(String loginId, Long connectionId) {
        return Admin.builder()
                .loginId(loginId)
                .password("password")
                .name(loginId)
                .role("ADMIN")
                .kakaoSenderConnectionId(connectionId)
                .build();
    }

    private User saveUser(String hiId) {
        return userRepository.save(User.builder()
                .hiId(hiId)
                .hiPassword("password")
                .name(hiId)
                .build());
    }

    private UserKakaoRecipient recipient(Long userId, Long kakaoUserId, String uuid) {
        return UserKakaoRecipient.builder()
                .userId(userId)
                .kakaoUserId(kakaoUserId)
                .kakaoMessageUuid(uuid)
                .build();
    }
}
