package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DemoYoutubeInspectionProviderTest {

    @Test
    void providesFixedInspectionOnlyForDemoYoutubeAccount() {
        SelectorsSnsAccountRepository repository =
                mock(SelectorsSnsAccountRepository.class);
        when(repository.findBySelectorsIdAndDeletedFalse(7L)).thenReturn(Optional.of(
                SelectorsSnsAccount.builder()
                        .selectorsId(7L)
                        .snsCode(SnsPlatform.YOUTUBE)
                        .accountId(DemoYoutubeInspectionProvider.ACCOUNT_ID)
                        .build()));
        DemoYoutubeInspectionProvider provider =
                new DemoYoutubeInspectionProvider(repository);
        Content content = Content.builder()
                .selectorsId(7L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("video")
                .contentUrl("https://youtu.be/video")
                .contentType(com.fuma.hiselectors.content.model.ContentType.SHORT_FORM)
                .build();

        assertThat(provider.supports(content)).isTrue();
        assertThat(provider.extraction().stt().segments()).hasSize(4);
        assertThat(provider.extraction().ocr().segments()).hasSize(9);
        assertThat(provider.extraction().report().overview().summary())
                .contains("현대백화점");
        assertThat(provider.violations(62L)).singleElement().satisfies(violation -> {
            assertThat(violation.type().name()).isEqualTo("FALSE_EXAGGERATED_CLAIM");
            assertThat(violation.evidence().confidence()).isEqualTo(1.0);
            assertThat(violation.evidence().locations()).singleElement().satisfies(location -> {
                assertThat(location.contentMediaId()).isEqualTo(62L);
                assertThat(location.segmentId()).isEqualTo("stt-002");
                assertThat(location.targetKind().name()).isEqualTo("STT_SEGMENT");
            });
        });
    }
}
