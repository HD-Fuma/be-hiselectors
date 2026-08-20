package com.fuma.hiselectors.selectors.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(SelectorsSnsAccountRepositoryTest.CacheConfig.class)
class SelectorsSnsAccountRepositoryTest {

    @Autowired
    private SelectorsSnsAccountRepository accountRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("셀렉터스에는 SNS 계정을 하나만 연결할 수 있다")
    void allowOnlyOneAccountPerSelectors() {
        accountRepository.save(SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .accountId("youtube-channel")
                .followerCount(1_000L)
                .build());
        accountRepository.save(SelectorsSnsAccount.builder()
                .selectorsId(2L)
                .snsCode(SnsPlatform.YOUTUBE)
                .accountId("other-channel")
                .build());

        assertThatThrownBy(() -> accountRepository.saveAndFlush(SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("instagram-account")
                .followerCount(2_000L)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("최초 수집 전에는 수집 완료 시각을 저장하지 않는다")
    void leaveLastCollectedAtNullBeforeFirstCollection() {
        SelectorsSnsAccount saved = accountRepository.saveAndFlush(
                SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.YOUTUBE)
                        .accountId("youtube-channel")
                        .build());

        entityManager.clear();

        assertThat(accountRepository.findById(saved.getId()).orElseThrow()
                .getLastCollectedAt()).isNull();
    }

    @Test
    @DisplayName("수집 완료 시각을 저장한다")
    void updateLastCollectedAt() {
        SelectorsSnsAccount account = accountRepository.save(
                SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.YOUTUBE)
                        .accountId("youtube-channel")
                        .build());
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 13, 15, 0);

        account.completeCollection(collectedAt);
        entityManager.flush();
        entityManager.clear();

        SelectorsSnsAccount found = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(found.getLastCollectedAt()).isEqualTo(collectedAt);
    }

    @TestConfiguration
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
