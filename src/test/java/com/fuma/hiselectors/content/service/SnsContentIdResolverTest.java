package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import org.junit.jupiter.api.Test;

class SnsContentIdResolverTest {

    @Test
    void usesYoutubeVideoIdAsIs() {
        assertThat(SnsContentIdResolver.resolve(
                SnsPlatform.YOUTUBE, "FcceHtZRS5I", "https://www.youtube.com/shorts/other"))
                .isEqualTo("FcceHtZRS5I");
    }

    @Test
    void parsesYoutubeShortsUrlWhenIdIsMissing() {
        assertThat(SnsContentIdResolver.resolve(
                SnsPlatform.YOUTUBE,
                "https://www.youtube.com/shorts/FcceHtZRS5I",
                null))
                .isEqualTo("FcceHtZRS5I");
    }

    @Test
    void parsesYoutubeWatchAndShortLinks() {
        assertThat(SnsContentIdResolver.resolve(
                SnsPlatform.YOUTUBE,
                null,
                "https://www.youtube.com/watch?v=_LiARJTy_ZU&t=3"))
                .isEqualTo("_LiARJTy_ZU");
        assertThat(SnsContentIdResolver.resolve(
                SnsPlatform.YOUTUBE,
                "",
                "https://youtu.be/jS6s-4UGX1w"))
                .isEqualTo("jS6s-4UGX1w");
    }

    @Test
    void rejectsInstagramPermalinkWithoutMediaId() {
        assertThat(SnsContentIdResolver.resolve(
                SnsPlatform.INSTAGRAM,
                "https://www.instagram.com/reel/abc123",
                "https://www.instagram.com/p/abc123"))
                .isNull();
    }

    @Test
    void usesNumericInstagramMediaId() {
        assertThat(SnsContentIdResolver.resolve(
                SnsPlatform.INSTAGRAM, "17841405309211844", "https://www.instagram.com/p/abc"))
                .isEqualTo("17841405309211844");
    }
}
