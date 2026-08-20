package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.classifier.SelectorsContentClassifier;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewContentServiceTest {

    @Mock
    private GenerationService generationService;

    @Mock
    private ContentBatchAccountRepository accountRepository;

    @Mock
    private ContentFetcher fetcher;

    @Mock
    private SelectorsContentClassifier classifier;

    @Mock
    private ContentRepository contentRepository;

    private NewContentService service;

    @BeforeEach
    void setUp() {
        service = new NewContentService(
                generationService,
                accountRepository,
                List.of(fetcher),
                classifier,
                contentRepository);
    }

    @Test
    void createsCollectionTargetsForCurrentGeneration() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        when(generation.getId()).thenReturn(3L);
        when(generation.getStartDate()).thenReturn(generationStart);

        SelectorsSnsAccount neverCollected = account(null);
        SelectorsSnsAccount collectedBeforeGeneration =
                account(generationStart.minusDays(1));
        SelectorsSnsAccount collectedDuringGeneration =
                account(generationStart.plusHours(2));
        when(generationService.getActive()).thenReturn(generation);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(
                neverCollected, collectedBeforeGeneration, collectedDuringGeneration));

        List<NewContentService.CollectionTarget> targets = service.collectionTargets();

        assertThat(targets).extracting(NewContentService.CollectionTarget::account)
                .containsExactly(
                        neverCollected, collectedBeforeGeneration, collectedDuringGeneration);
        assertThat(targets).extracting(NewContentService.CollectionTarget::since)
                .containsExactly(
                        generationStart, generationStart, generationStart.plusHours(2));
        verify(accountRepository).findAllByGenerationId(3L);
    }

    @Test
    void selectsOnlyNewSelectorsContentCandidates() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 20, 10, 0);
        SelectorsSnsAccount account = SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("instagram-account")
                .build();
        NewContentService.CollectionTarget target =
                new NewContentService.CollectionTarget(account, since);
        RawContent existing = raw("existing", "셀렉터스 콘텐츠");
        RawContent duplicate = raw("duplicate", "셀렉터스 콘텐츠");
        RawContent rejected = raw("rejected", "일반 콘텐츠");
        RawContent selected = raw("selected", "더현대 셀렉터스");

        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("instagram-account", since))
                .thenReturn(new ContentFetcher.CollectionResult(5, List.of(
                        existing, duplicate, duplicate, rejected, selected)));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM,
                List.of("existing", "duplicate", "rejected", "selected")))
                .thenReturn(List.of(Content.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .snsContentId("existing")
                        .contentUrl("https://example.com/existing")
                        .contentType(ContentType.FEED)
                        .build()));
        when(classifier.isSelectorsContent(duplicate)).thenReturn(true);
        when(classifier.isSelectorsContent(rejected)).thenReturn(false);
        when(classifier.isSelectorsContent(selected)).thenReturn(true);

        List<RawContent> candidates = service.newCandidates(target);

        assertThat(candidates).containsExactly(duplicate, selected);
        verify(fetcher).fetchByAccount("instagram-account", since);
    }

    private SelectorsSnsAccount account(LocalDateTime lastCollectedAt) {
        return SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .accountId("account")
                .lastCollectedAt(lastCollectedAt)
                .build();
    }

    private RawContent raw(String id, String caption) {
        return new RawContent(
                SnsPlatform.INSTAGRAM,
                id,
                "https://example.com/" + id,
                ContentType.FEED,
                caption,
                LocalDateTime.of(2026, 8, 20, 11, 0),
                List.of());
    }
}
