package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.HiselectorsApplication;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/** 실제 외부 API 호출과 로컬 DB 저장 수동 검증 */
@Tag("external-api")
@EnabledIfEnvironmentVariable(named = "RUN_CONTENT_SERVICE_TEST", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(classes = HiselectorsApplication.class)
@Transactional
class ContentCollectionServiceSmokeTest {

    private static final Long SELECTORS_SNS_ACCOUNT_ID = 1L;
    private static final String INSTAGRAM_USERNAME = "y__njin_";
    private static final LocalDateTime LAST_COLLECTED_AT =
            LocalDateTime.of(2026, 5, 1, 0, 0);
    private static final String EXPECTED_CONTENT_URL =
            "https://www.instagram.com/reel/DawgQxlBCMt/";

    @Autowired
    private ContentCollectionService collectionService;

    @Autowired
    private SelectorsSnsAccountRepository accountRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentVersionRepository versionRepository;

    @Autowired
    private ContentMediaRepository mediaRepository;

    @Test
    void collectAndSaveContentsFromRealApi() {
        SelectorsSnsAccount beforeAccount = accountRepository
                .findById(SELECTORS_SNS_ACCOUNT_ID)
                .orElseThrow();
        assertThat(beforeAccount.getAccountId()).isEqualTo(INSTAGRAM_USERNAME);
        ReflectionTestUtils.setField(beforeAccount, "lastCollectedAt", LAST_COLLECTED_AT);
        Set<Long> beforeContentIds = contentRepository.findAll().stream()
                .map(Content::getId)
                .collect(Collectors.toSet());

        int savedCount = collectionService.collectForAccount(SELECTORS_SNS_ACCOUNT_ID);

        SelectorsSnsAccount afterAccount = accountRepository
                .findById(SELECTORS_SNS_ACCOUNT_ID)
                .orElseThrow();
        List<Content> savedContents = contentRepository.findAll().stream()
                .filter(content -> !beforeContentIds.contains(content.getId()))
                .toList();
        Set<Long> savedContentIds = savedContents.stream()
                .map(Content::getId)
                .collect(Collectors.toSet());
        List<ContentVersion> savedVersions = versionRepository.findAll().stream()
                .filter(version -> savedContentIds.contains(version.getContentId()))
                .toList();
        Set<Long> savedVersionIds = savedVersions.stream()
                .map(ContentVersion::getId)
                .collect(Collectors.toSet());
        List<ContentMedia> savedMedia = mediaRepository.findAll().stream()
                .filter(media -> savedVersionIds.contains(media.getContentVersionId()))
                .toList();

        assertThat(savedContents).hasSize(savedCount);
        assertThat(savedVersions).hasSameSizeAs(savedContents);
        assertThat(savedMedia).isNotEmpty();
        assertThat(savedContents)
                .extracting(Content::getContentUrl)
                .contains(EXPECTED_CONTENT_URL);
        assertThat(afterAccount.getLastCollectedAt())
                .isAfterOrEqualTo(LAST_COLLECTED_AT);
    }
}
