package com.fuma.hiselectors.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.config.JpaAuditingConfig;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.user.model.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({JpaAuditingConfig.class, CacheConfig.class})
class ApplicationAdminRepositoryTest {

    private static final LocalDateTime COLLECTED_AT =
            LocalDateTime.of(2026, 8, 20, 12, 0);

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationMediaRepository mediaRepository;

    @Autowired
    private TestEntityManager em;

    private Generation generation;
    private Application regular;
    private Application lowFollower;
    private Application sparse;
    private Application unknown;

    @BeforeEach
    void setUp() {
        generation = em.persist(Generation.builder().generationName("2기").build());
        regular = saveApplication("지안", "jian", SnsPlatform.INSTAGRAM,
                ApplicationStatus.PENDING, 1_000L, true, 4);
        lowFollower = saveApplication("민희", "UC-low", SnsPlatform.YOUTUBE,
                ApplicationStatus.REJECTED, 400L, false, 0);
        sparse = saveApplication("수빈", "UC-sparse", SnsPlatform.YOUTUBE,
                ApplicationStatus.PENDING, 1_000L, true, 3);
        unknown = saveApplication("다온", "UC-unknown", SnsPlatform.YOUTUBE,
                ApplicationStatus.PENDING, null, false, 0);
        mediaRepository.save(ApplicationMedia.builder()
                .applicationId(sparse.getId())
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("UC-sparse-outside-window")
                .contentUrl("https://example.com/UC-sparse/outside-window")
                .sequenceNo(3)
                .publishedAt(COLLECTED_AT.minusDays(91))
                .collectedAt(COLLECTED_AT)
                .build());
        em.flush();
        em.clear();
    }

    @Test
    void filtersKeywordPlatformStatusAndGenerationTogether() {
        var result = applicationRepository.searchAdmin(
                "지안", SnsPlatform.INSTAGRAM, ApplicationStatus.PENDING,
                generation.getId(), null, PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(Application::getId)
                .containsExactly(regular.getId());
    }

    @Test
    void omittedMinimumCriteriaReturnsAllApplicants() {
        var result = applicationRepository.searchAdmin(
                null, null, null, generation.getId(), null, PageRequest.of(0, 2));

        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(applicationRepository.searchAdmin(
                null, null, null, generation.getId(), null, PageRequest.of(0, 20)))
                .extracting(Application::getId)
                .containsExactlyInAnyOrder(
                        regular.getId(), lowFollower.getId(), sparse.getId(), unknown.getId());
    }

    @Test
    void minimumCriteriaIsAppliedBeforePaging() {
        var result = applicationRepository.searchAdmin(
                null, null, null, generation.getId(), true, PageRequest.of(0, 1));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(applicationRepository.searchAdmin(
                null, null, null, generation.getId(), true, PageRequest.of(0, 20)))
                .extracting(Application::getId)
                .containsExactlyInAnyOrder(lowFollower.getId(), sparse.getId())
                .doesNotContain(regular.getId());
    }

    @Test
    void falseMinimumCriteriaReturnsOnlyApplicantsWhoAreNotBelowCriteria() {
        var result = applicationRepository.searchAdmin(
                null, null, null, generation.getId(), false, PageRequest.of(0, 1));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(applicationRepository.searchAdmin(
                null, null, null, generation.getId(), false, PageRequest.of(0, 20)))
                .extracting(Application::getId)
                .containsExactlyInAnyOrder(regular.getId(), unknown.getId())
                .doesNotContain(lowFollower.getId(), sparse.getId());
    }

    @Test
    void minimumCriteriaIgnoresContentsOutsideCollectionWindow() {
        assertThat(mediaRepository.findAllByApplicationIdOrderBySequenceNoAsc(sparse.getId()))
                .hasSize(4);

        var result = applicationRepository.searchAdmin(
                "UC-sparse", null, null, generation.getId(), true, PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(Application::getId)
                .containsExactly(sparse.getId());
    }

    private Application saveApplication(
            String name,
            String accountId,
            SnsPlatform platform,
            ApplicationStatus status,
            Long followers,
            boolean collected,
            int mediaCount) {
        User user = em.persist(User.builder()
                .hiId("hi-" + accountId)
                .name(name)
                .email(accountId + "@example.com")
                .build());
        Application application = Application.builder()
                .userId(user.getId())
                .generationId(generation.getId())
                .snsCode(platform)
                .snsAccountId(accountId)
                .followerCount(followers)
                .alarmYn(true)
                .policyAgreedAt(COLLECTED_AT.minusDays(30))
                .status(status)
                .build();
        if (collected) {
            application.completeMediaCollection(COLLECTED_AT);
        }
        application = applicationRepository.save(application);
        for (int sequenceNo = 0; sequenceNo < mediaCount; sequenceNo++) {
            mediaRepository.save(ApplicationMedia.builder()
                    .applicationId(application.getId())
                    .snsCode(platform)
                    .snsContentId(accountId + "-" + sequenceNo)
                    .contentUrl("https://example.com/" + accountId + "/" + sequenceNo)
                    .contentType(platform == SnsPlatform.INSTAGRAM ? ContentType.FEED : null)
                    .sequenceNo(sequenceNo)
                    .publishedAt(COLLECTED_AT.minusDays(sequenceNo))
                    .collectedAt(COLLECTED_AT)
                    .build());
        }
        return application;
    }
}
