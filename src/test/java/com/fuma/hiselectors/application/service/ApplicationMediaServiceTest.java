package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.ContentPlatformClient;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ApplicationMediaServiceTest {

    private static final Long APPLICATION_ID = 1L;

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ApplicationMediaRepository mediaRepository;
    @Mock
    private ContentPlatformClient instagramClient;
    @Mock
    private ContentPlatformClient youtubeClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ApplicationMediaService service;

    @BeforeEach
    void setUp() {
        lenient().when(instagramClient.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        lenient().when(youtubeClient.supports()).thenReturn(SnsPlatform.YOUTUBE);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        service = new ApplicationMediaService(
                applicationRepository, mediaRepository,
                List.of(instagramClient, youtubeClient), transactionTemplate);
    }

    @Test
    void collectStoresLatestTenDistinctContents() {
        Application application = application(SnsPlatform.YOUTUBE, "channel-id");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        LocalDateTime now = LocalDateTime.now();
        List<RawContent> contents = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            contents.add(i == 0
                    ? raw(SnsPlatform.YOUTUBE, "video-0", now, 100L, 20L, 3L)
                    : raw(SnsPlatform.YOUTUBE, "video-" + i, now.minusDays(i)));
        }
        contents.add(raw(SnsPlatform.YOUTUBE, "video-0", now.minusHours(1)));
        contents.add(raw(SnsPlatform.YOUTUBE, "old", now.minusDays(91)));
        contents.add(raw(SnsPlatform.INSTAGRAM, "wrong-platform", now));
        when(youtubeClient.collect(any(), any()))
                .thenReturn(new ContentPlatformClient.CollectionResult(contents.size(), contents));

        AtomicReference<List<ApplicationMedia>> saved = new AtomicReference<>();
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ApplicationMedia> values = invocation.getArgument(0);
            saved.set(values);
            return values;
        });

        var result = service.collect(APPLICATION_ID);

        assertThat(result.fetchedCount()).isEqualTo(contents.size());
        assertThat(result.storedCount()).isEqualTo(10);
        assertThat(saved.get())
                .extracting(ApplicationMedia::getSnsContentId)
                .containsExactly(
                        "video-0", "video-1", "video-2", "video-3", "video-4",
                        "video-5", "video-6", "video-7", "video-8", "video-9");
        assertThat(saved.get())
                .extracting(ApplicationMedia::getSequenceNo)
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(saved.get().getFirst()).satisfies(media -> {
            assertThat(media.getViewCount()).isEqualTo(100L);
            assertThat(media.getLikeCount()).isEqualTo(20L);
            assertThat(media.getCommentCount()).isEqualTo(3L);
        });
        verify(mediaRepository).deleteByApplicationId(APPLICATION_ID);
        verify(mediaRepository).flush();
    }

    @Test
    void collectKeepsExistingSnapshotWhenClientFails() {
        Application application = application(SnsPlatform.YOUTUBE, "channel-id");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(youtubeClient.collect(any(), any())).thenThrow(new IllegalStateException("API failed"));

        assertThatThrownBy(() -> service.collect(APPLICATION_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(mediaRepository, never()).deleteByApplicationId(any());
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    void findLatestReturnsStoredContents() {
        Application application = application(SnsPlatform.INSTAGRAM, "username");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(mediaRepository.findTop3ByApplicationIdOrderBySequenceNoAsc(APPLICATION_ID))
                .thenReturn(List.of(media("post-1", 0), media("post-2", 1), media("post-3", 2)));

        var result = service.findLatest(APPLICATION_ID);

        assertThat(result)
                .extracting(response -> response.snsContentId())
                .containsExactly("post-1", "post-2", "post-3");
    }

    private Application application(SnsPlatform platform, String accountId) {
        Application application = Application.builder()
                .userId(10L)
                .generationId(20L)
                .snsCode(platform)
                .snsAccountId(accountId)
                .alarmYn(true)
                .policyAgreedAt(LocalDateTime.now())
                .status(ApplicationStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        return application;
    }

    private RawContent raw(SnsPlatform platform, String contentId, LocalDateTime createdAt) {
        return raw(platform, contentId, createdAt, null, null, null);
    }

    private RawContent raw(SnsPlatform platform, String contentId, LocalDateTime createdAt,
                           Long viewCount, Long likeCount, Long commentCount) {
        return new RawContent(
                platform,
                contentId,
                "https://example.com/" + contentId,
                ContentType.FEED,
                List.of(),
                createdAt,
                List.of(),
                viewCount,
                likeCount,
                commentCount);
    }

    private ApplicationMedia media(String contentId, int sequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(APPLICATION_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId(contentId)
                .mediaUrl("https://example.com/" + contentId)
                .sequenceNo(sequenceNo)
                .publishedAt(LocalDateTime.now().minusDays(sequenceNo))
                .build();
    }
}
