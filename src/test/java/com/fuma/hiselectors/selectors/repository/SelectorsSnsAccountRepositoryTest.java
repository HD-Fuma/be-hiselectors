package com.fuma.hiselectors.selectors.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
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
    private ContentBatchAccountRepository batchAccountRepository;

    @Autowired
    private ContentRepository contentRepository;

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
    @DisplayName("SNS 프로필 URL을 저장한다")
    void saveProfileUrl() {
        SelectorsSnsAccount saved = accountRepository.saveAndFlush(
                SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.YOUTUBE)
                        .accountId("youtube-channel")
                        .profileUrl("https://www.youtube.com/channel/youtube-channel")
                        .build());

        entityManager.clear();

        assertThat(accountRepository.findById(saved.getId()).orElseThrow().getProfileUrl())
                .isEqualTo("https://www.youtube.com/channel/youtube-channel");
    }

    @Test
    @DisplayName("같은 SNS 계정의 새 프로필 URL이 없으면 기존 URL을 유지한다")
    void keepProfileUrlWhenSameAccountHasNoNewUrl() {
        SelectorsSnsAccount account = accountRepository.saveAndFlush(
                SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.YOUTUBE)
                        .accountId("youtube-channel")
                        .profileUrl("https://www.youtube.com/channel/youtube-channel")
                        .build());

        account.synchronize(SnsPlatform.YOUTUBE, "youtube-channel", 2_000L, null);
        entityManager.flush();
        entityManager.clear();

        SelectorsSnsAccount found = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(found.getProfileUrl())
                .isEqualTo("https://www.youtube.com/channel/youtube-channel");
        assertThat(found.getFollowerCount()).isEqualTo(2_000L);
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

    @Test
    @DisplayName("수집 중 SNS 계정이 바뀌면 이전 계정의 커서를 저장하지 않는다")
    void updateCursorOnlyForUnchangedAccount() {
        SelectorsSnsAccount account = accountRepository.saveAndFlush(
                SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.YOUTUBE)
                        .accountId("youtube-channel")
                        .build());
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 13, 15, 0);

        assertThat(batchAccountRepository.advanceCollectionCursorIfAccountUnchanged(
                account.getId(), SnsPlatform.YOUTUBE, "youtube-channel", collectedAt)).isOne();
        entityManager.clear();
        assertThat(accountRepository.findById(account.getId()).orElseThrow()
                .getLastCollectedAt()).isEqualTo(collectedAt);

        SelectorsSnsAccount changed = accountRepository.findById(account.getId()).orElseThrow();
        changed.synchronize(SnsPlatform.INSTAGRAM, "instagram-account", 100L, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(batchAccountRepository.advanceCollectionCursorIfAccountUnchanged(
                account.getId(), SnsPlatform.YOUTUBE, "youtube-channel",
                collectedAt.plusHours(1))).isZero();
        entityManager.clear();
        SelectorsSnsAccount found = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(found.getSnsCode()).isEqualTo(SnsPlatform.INSTAGRAM);
        assertThat(found.getAccountId()).isEqualTo("instagram-account");
        assertThat(found.getLastCollectedAt()).isNull();
    }

    @Test
    @DisplayName("활동 중인 셀렉터스의 SNS 계정과 콘텐츠만 수집 대상으로 조회한다")
    void findOnlyActiveSelectorsDataForContentCollection() {
        Long generationId = 1L;
        Selectors activeSelectors = entityManager.persist(Selectors.builder()
                .userId(1L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .build());
        Selectors inactiveSelectors = entityManager.persist(Selectors.builder()
                .userId(2L)
                .selectorsRoleId(Selectors.INACTIVE_ROLE)
                .build());
        entityManager.persist(SelectorsGeneration.builder()
                .selectorsId(activeSelectors.getId())
                .generationId(generationId)
                .build());
        entityManager.persist(SelectorsGeneration.builder()
                .selectorsId(inactiveSelectors.getId())
                .generationId(generationId)
                .build());
        SelectorsSnsAccount activeAccount = entityManager.persist(
                SelectorsSnsAccount.builder()
                        .selectorsId(activeSelectors.getId())
                        .snsCode(SnsPlatform.YOUTUBE)
                        .accountId("active-channel")
                        .build());
        entityManager.persist(SelectorsSnsAccount.builder()
                .selectorsId(inactiveSelectors.getId())
                .snsCode(SnsPlatform.YOUTUBE)
                .accountId("inactive-channel")
                .build());
        Content activeContent = entityManager.persist(Content.builder()
                .selectorsId(activeSelectors.getId())
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("active-content")
                .contentUrl("https://example.com/active-content")
                .contentType(ContentType.SHORTS)
                .build());
        entityManager.persist(Content.builder()
                .selectorsId(inactiveSelectors.getId())
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("inactive-content")
                .contentUrl("https://example.com/inactive-content")
                .contentType(ContentType.SHORTS)
                .build());
        entityManager.flush();
        entityManager.clear();

        assertThat(batchAccountRepository.findAllByGenerationId(generationId))
                .extracting(SelectorsSnsAccount::getId)
                .containsExactly(activeAccount.getId());
        assertThat(contentRepository.findAllByGenerationId(generationId))
                .extracting(Content::getId)
                .containsExactly(activeContent.getId());
    }

    @Test
    @DisplayName("삭제된 SNS 계정은 새 행 없이 승인 정보로 재활성화한다")
    void synchronizeDeletedAccountWithoutDuplicate() {
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 13, 15, 0);
        SelectorsSnsAccount account = accountRepository.saveAndFlush(
                SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .accountId("old-account")
                        .profileUrl("https://www.instagram.com/old-account/")
                        .followerCount(10L)
                        .deleted(true)
                        .lastCollectedAt(collectedAt)
                        .profileImageUrl("https://old.example/profile.jpg")
                        .build());

        account.synchronize(SnsPlatform.YOUTUBE, "UC-approved", 12_345L, null);
        entityManager.flush();
        entityManager.clear();

        SelectorsSnsAccount found = accountRepository.findBySelectorsId(1L).orElseThrow();
        assertThat(found.getId()).isEqualTo(account.getId());
        assertThat(found.getSnsCode()).isEqualTo(SnsPlatform.YOUTUBE);
        assertThat(found.getAccountId()).isEqualTo("UC-approved");
        assertThat(found.getProfileUrl()).isNull();
        assertThat(found.getFollowerCount()).isEqualTo(12_345L);
        assertThat(found.isDeleted()).isFalse();
        assertThat(found.getLastCollectedAt()).isNull();
        assertThat(found.getProfileImageUrl()).isNull();
        assertThat(accountRepository.count()).isOne();
    }

    @TestConfiguration
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
