package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import org.junit.jupiter.api.Test;

class ContentBatchModeTest {

    @Test
    void fastModeIncludesOnlyConfiguredYoutubeAndInstagramAccounts() {
        assertThat(ContentBatchMode.FAST.includes(account(
                SnsPlatform.YOUTUBE, "UCD2RQE52TloxzZxZ2fyq8HQ"))).isTrue();
        assertThat(ContentBatchMode.FAST.includes(account(
                SnsPlatform.INSTAGRAM, "@HI_SELECTORS"))).isTrue();
        assertThat(ContentBatchMode.FAST.includes(account(
                SnsPlatform.YOUTUBE, "another-channel"))).isFalse();
        assertThat(ContentBatchMode.FAST.includes(account(
                SnsPlatform.INSTAGRAM, "another_handle"))).isFalse();
        assertThat(ContentBatchMode.STANDARD.includes(account(
                SnsPlatform.INSTAGRAM, "another_handle"))).isTrue();
    }

    private SelectorsSnsAccount account(SnsPlatform platform, String accountId) {
        return SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(platform)
                .accountId(accountId)
                .build();
    }
}
