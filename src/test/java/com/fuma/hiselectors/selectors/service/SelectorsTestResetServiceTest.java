package com.fuma.hiselectors.selectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.selectors.dto.SelectorsTestResetResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsTestResetRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({
        SelectorsTestResetServiceTest.ResetConfig.class,
        SelectorsTestResetRepository.class,
        SelectorsTestResetService.class,
})
class SelectorsTestResetServiceTest {

    private static final Long GENERATION_ID = 3L;
    private static final Long USER_ID = 100L;

    @Autowired
    private SelectorsTestResetService resetService;

    @Autowired
    private SelectorsRepository selectorsRepository;

    @Autowired
    private SelectorsSnsAccountRepository accountRepository;

    @Autowired
    private SelectorsGenerationRepository generationRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentVersionRepository contentVersionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("SNS 계정으로 찾은 셀렉터스와 지원서, 매달린 콘텐츠까지 지운다")
    void deleteEverythingHangingOffTheAccount() {
        Long applicationId = persistApplication("harin.daily");
        Long selectorsId = persistSelectors(applicationId, "harin.daily");
        Long contentId = persistContentWithVersion(selectorsId);
        entityManager.flush();
        entityManager.clear();

        SelectorsTestResetResponse response = resetService.reset(
                SnsPlatform.INSTAGRAM, "harin.daily", "admin");

        assertThat(response.selectorsIds()).containsExactly(selectorsId);
        assertThat(response.applicationIds()).containsExactly(applicationId);
        assertThat(response.deletedRowCounts())
                .containsEntry("selectors", 1)
                .containsEntry("selectors_sns_account", 1)
                .containsEntry("selectors_generation", 1)
                .containsEntry("content", 1)
                .containsEntry("content_version", 1)
                .containsEntry("application", 1);
        assertThat(response.deletedRowCount()).isEqualTo(6);

        entityManager.clear();
        assertThat(selectorsRepository.findById(selectorsId)).isEmpty();
        assertThat(accountRepository.findBySelectorsId(selectorsId)).isEmpty();
        assertThat(generationRepository.findAll()).isEmpty();
        assertThat(applicationRepository.findById(applicationId)).isEmpty();
        assertThat(contentRepository.findById(contentId)).isEmpty();
        assertThat(contentVersionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("앞에 @ 를 붙여 입력해도 같은 계정을 찾는다")
    void matchAccountRegardlessOfLeadingAt() {
        Long applicationId = persistApplication("harin.daily");
        entityManager.flush();
        entityManager.clear();

        SelectorsTestResetResponse response = resetService.reset(
                SnsPlatform.INSTAGRAM, " @Harin.Daily ", "admin");

        assertThat(response.accountId()).isEqualTo("harin.daily");
        assertThat(response.applicationIds()).containsExactly(applicationId);
    }

    @Test
    @DisplayName("다른 플랫폼의 같은 계정명은 건드리지 않는다")
    void leaveOtherPlatformsAlone() {
        Long instagramApplicationId = persistApplication("harin.daily");
        Long youtubeApplicationId = entityManager.persist(Application.builder()
                .userId(USER_ID + 1)
                .generationId(GENERATION_ID)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsAccountId("harin.daily")
                .alarmYn(false)
                .policyAgreedAt(LocalDateTime.now())
                .status(ApplicationStatus.PENDING)
                .build()).getId();
        entityManager.flush();
        entityManager.clear();

        resetService.reset(SnsPlatform.INSTAGRAM, "harin.daily", "admin");

        entityManager.clear();
        assertThat(applicationRepository.findById(instagramApplicationId)).isEmpty();
        assertThat(applicationRepository.findById(youtubeApplicationId)).isPresent();
    }

    @Test
    @DisplayName("지원 이력도 셀렉터스도 없으면 삭제 없이 404 를 던진다")
    void rejectUnknownAccount() {
        assertThatThrownBy(() -> resetService.reset(SnsPlatform.INSTAGRAM, "nobody", "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nobody");
    }

    @Test
    @DisplayName("계정명이 비어 있으면 400 을 던진다")
    void rejectBlankAccount() {
        assertThatThrownBy(() -> resetService.reset(SnsPlatform.INSTAGRAM, " @ ", "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SNS 계정명");
    }

    private Long persistApplication(String accountId) {
        return entityManager.persist(Application.builder()
                .userId(USER_ID)
                .generationId(GENERATION_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsAccountId(accountId)
                .alarmYn(false)
                .policyAgreedAt(LocalDateTime.now())
                .status(ApplicationStatus.PENDING)
                .build()).getId();
    }

    private Long persistSelectors(Long applicationId, String accountId) {
        Selectors selectors = entityManager.persist(Selectors.builder()
                .applicationId(applicationId)
                .userId(USER_ID)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsCode("SEL-0001")
                .selectorsNickname("하린데일리")
                .build());
        entityManager.persist(SelectorsSnsAccount.builder()
                .selectorsId(selectors.getId())
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId(accountId)
                .build());
        entityManager.persist(SelectorsGeneration.builder()
                .selectorsId(selectors.getId())
                .generationId(GENERATION_ID)
                .build());
        return selectors.getId();
    }

    private Long persistContentWithVersion(Long selectorsId) {
        Content content = entityManager.persist(Content.builder()
                .selectorsId(selectorsId)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("https://www.instagram.com/p/abc/")
                .contentUrl("https://www.instagram.com/p/abc/")
                .contentType(ContentType.REELS)
                .build());
        entityManager.persist(ContentVersion.create(content.getId(), 1L, "hash-1"));
        return content.getId();
    }

    @TestConfiguration
    static class ResetConfig {

        @Bean
        BatchEventLogger batchEventLogger() {
            return new BatchEventLogger(new ObjectMapper(), Clock.systemUTC());
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
