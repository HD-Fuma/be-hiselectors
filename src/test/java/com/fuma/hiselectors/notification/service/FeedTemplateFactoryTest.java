package com.fuma.hiselectors.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.kakao.config.KakaoMessageProperties;
import com.fuma.hiselectors.kakao.dto.DefaultFeedTemplate;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedTemplateFactoryTest {

    private final FeedTemplateFactory factory = new FeedTemplateFactory(
            new KakaoMessageProperties("https://example.com", "https://m.example.com",
                    "https://example.com/image.png", ""),
            new NotificationMessageFactory());

    @Test
    @DisplayName("승인 메시지를 DEFAULT_FEED와 버튼으로 생성한다")
    void approvedFeed() {
        var created = factory.create(new NotificationMessageCommand(
                1L, 2L, 3L, "홍길동", null, NotificationType.SELECTION_APPROVED));
        assertThat(created.template()).isInstanceOf(DefaultFeedTemplate.class);
        DefaultFeedTemplate template = (DefaultFeedTemplate) created.template();

        assertThat(template.objectType()).isEqualTo("feed");
        assertThat(template.content().description()).contains("홍길동", "선정");
        assertThat(template.buttons()).singleElement()
                .extracting(button -> button.title()).isEqualTo("활동 가이드 확인하기");
        assertThat(created.body().length()).isLessThanOrEqualTo(400);
    }

    @Test
    @DisplayName("콘텐츠 위반 메시지에 수정 상세를 포함한다")
    void violationFeed() {
        var created = factory.create(new NotificationMessageCommand(
                1L, 2L, 3L, "김철수", "금칙어를 수정해주세요.",
                NotificationType.CONTENT_EDIT_REQUEST));
        DefaultFeedTemplate template = (DefaultFeedTemplate) created.template();
        assertThat(template.content().description())
                .contains("수정", "금칙어를 수정해주세요.");
    }

    @Test
    @DisplayName("모든 알림 유형의 DEFAULT_FEED를 생성한다")
    void allNotificationTypesHaveFeed() {
        for (NotificationType type : NotificationType.values()) {
            var created = factory.create(new NotificationMessageCommand(
                    1L, 2L, 3L, "홍길동", "상세 안내", type));
            DefaultFeedTemplate template = (DefaultFeedTemplate) created.template();

            assertThat(template.content().title()).isNotBlank();
            assertThat(template.content().description()).isNotBlank();
            assertThat(template.buttons()).singleElement()
                    .extracting(button -> button.title()).asString().isNotBlank();
            assertThat(created.body().length()).isLessThanOrEqualTo(400);
        }
    }
}
