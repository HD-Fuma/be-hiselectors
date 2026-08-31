package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentInspectionDecision;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CacheConfig.class)
class ContentVersionRepositoryStaleQueryTest {

    private static final Long ACTIVE_POLICY_ID = 9L;
    private static final Long ACTIVE_GENERATION_ID = 10L;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentVersionRepository contentVersionRepository;

    @Autowired
    private ContentReportRepository contentReportRepository;

    @Autowired
    private SelectorsRepository selectorsRepository;

    @Autowired
    private SelectorsGenerationRepository selectorsGenerationRepository;

    @Autowired
    private SelectorsSnsAccountRepository selectorsSnsAccountRepository;

    private Long selectorsId;

    @BeforeEach
    void setUpGenerationScope() {
        Selectors selectors = selectorsRepository.save(Selectors.builder()
                .userId(100L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .build());
        selectorsId = selectors.getId();
        selectorsGenerationRepository.save(SelectorsGeneration.builder()
                .selectorsId(selectorsId)
                .generationId(ACTIVE_GENERATION_ID)
                .build());
    }

    @Test
    void selectsOnlyLatestVersionsMissingOrMismatchedEngineStamp() {
        Long noReportLatest = latestWithoutReport();
        Long matchingLatest = latestWithPolicy(ACTIVE_POLICY_ID);
        Long mismatchedLatest = latestWithPolicy(1L);
        Long nullEngineLatest = latestWithPolicy(null);
        Long confirmedLatest = latestWithPolicy(1L);
        ContentVersion confirmedVersion = contentVersionRepository.findById(confirmedLatest).orElseThrow();
        confirmedVersion.startInspection();
        confirmedVersion.completeInspection(LocalDateTime.now());
        confirmedVersion.confirmInspection(ContentInspectionDecision.APPROVED);
        assertThatThrownBy(confirmedVersion::startInspection)
                .isInstanceOf(BusinessException.class);
        assertThat(confirmedVersion.getInspectionDecision())
                .isEqualTo(ContentInspectionDecision.APPROVED);
        Long inspectingLatest = latestWithoutReport();
        contentVersionRepository.findById(inspectingLatest).orElseThrow().startInspection();
        Long outOfGeneration = latestWithoutReportForSelectors(selectorsRepository.save(
                Selectors.builder().userId(200L)
                        .selectorsRoleId(Selectors.ACTIVE_ROLE).build()).getId());
        Content deletedContent = saveContent();
        deletedContent.markDeleted();
        Long deletedLatest = contentVersionRepository.save(ContentVersion.create(
                deletedContent.getId(), 1L, hash("deleted"))).getId();
        olderStaleVersionHiddenByMatchingLatest();

        List<Long> staleIds = contentVersionRepository.findStaleLatestVersionIds(
                ACTIVE_GENERATION_ID, SnsPlatform.INSTAGRAM, ACTIVE_POLICY_ID,
                ContentVersionStatus.INSPECTING, PageRequest.of(0, 50));

        assertThat(staleIds)
                .contains(noReportLatest, mismatchedLatest, nullEngineLatest)
                .doesNotContain(
                        matchingLatest, confirmedLatest, inspectingLatest,
                        outOfGeneration, deletedLatest);
    }

    @Test
    void respectsLimit() {
        latestWithoutReport();
        latestWithoutReport();
        latestWithoutReport();

        List<Long> staleIds = contentVersionRepository.findStaleLatestVersionIds(
                ACTIVE_GENERATION_ID, SnsPlatform.INSTAGRAM, ACTIVE_POLICY_ID,
                ContentVersionStatus.INSPECTING, PageRequest.of(0, 2));

        assertThat(staleIds).hasSize(2);
    }

    @Test
    void scopesStaleVersionsToTheRequestedPlatformAccount() {
        selectorsSnsAccountRepository.save(SelectorsSnsAccount.builder()
                .selectorsId(selectorsId)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("@HI_SELECTORS")
                .build());
        Long matching = latestWithoutReport();
        Selectors other = selectorsRepository.save(Selectors.builder()
                .userId(200L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .build());
        selectorsGenerationRepository.save(SelectorsGeneration.builder()
                .selectorsId(other.getId())
                .generationId(ACTIVE_GENERATION_ID)
                .build());
        selectorsSnsAccountRepository.save(SelectorsSnsAccount.builder()
                .selectorsId(other.getId())
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("another_handle")
                .build());
        Long unrelated = latestWithoutReportForSelectors(other.getId());

        List<Long> staleIds = contentVersionRepository.findStaleLatestVersionIds(
                ACTIVE_GENERATION_ID,
                SnsPlatform.INSTAGRAM,
                ACTIVE_POLICY_ID,
                ContentVersionStatus.INSPECTING,
                "hi_selectors",
                PageRequest.of(0, 50));

        assertThat(staleIds).containsExactly(matching).doesNotContain(unrelated);
    }

    @Test
    void appendsReportsAndReturnsLatestOne() {
        Content content = saveContent();
        ContentVersion version = contentVersionRepository.save(
                ContentVersion.create(content.getId(), 1L, hash("history")));
        ContentReport first = contentReportRepository.save(ContentReport.create(
                version.getId(), new ContentReportData("이전", "", "", ""), 1L));
        ContentReport latest = contentReportRepository.save(ContentReport.create(
                version.getId(), new ContentReportData("최신", "", "", ""), 2L));

        assertThat(contentReportRepository.findAll())
                .extracting(ContentReport::getId)
                .contains(first.getId(), latest.getId());
        assertThat(contentReportRepository
                .findFirstByContentVersionIdOrderByIdDesc(version.getId())
                .orElseThrow().getSummary()).isEqualTo("최신");
    }

    private Long latestWithoutReport() {
        return latestWithoutReportForSelectors(selectorsId);
    }

    private Long latestWithoutReportForSelectors(Long ownerSelectorsId) {
        Content content = saveContent(ownerSelectorsId);
        return contentVersionRepository.save(ContentVersion.create(
                content.getId(), 1L, hash("no-report"))).getId();
    }

    private Long latestWithPolicy(Long policyId) {
        Content content = saveContent();
        ContentVersion version = contentVersionRepository.save(
                ContentVersion.create(content.getId(), 1L, hash("stamped")));
        contentReportRepository.save(ContentReport.create(
                version.getId(), ContentReportData.empty(), policyId));
        return version.getId();
    }

    private void olderStaleVersionHiddenByMatchingLatest() {
        Content content = saveContent();
        ContentVersion oldVersion = contentVersionRepository.save(
                ContentVersion.create(content.getId(), 1L, hash("old")));
        ContentVersion latest = contentVersionRepository.save(
                ContentVersion.create(content.getId(), 2L, hash("new")));
        contentReportRepository.save(ContentReport.create(
                oldVersion.getId(), ContentReportData.empty(), 1L));
        contentReportRepository.save(ContentReport.create(
                latest.getId(), ContentReportData.empty(), ACTIVE_POLICY_ID));
    }

    private Content saveContent() {
        return saveContent(selectorsId);
    }

    private Content saveContent(Long ownerSelectorsId) {
        return contentRepository.save(Content.builder()
                .selectorsId(ownerSelectorsId)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("sns-" + System.nanoTime())
                .contentUrl("https://www.instagram.com/p/content")
                .contentType(ContentType.FEED)
                .build());
    }

    private static String hash(String seed) {
        return (seed + "x".repeat(64)).substring(0, 64);
    }
}
