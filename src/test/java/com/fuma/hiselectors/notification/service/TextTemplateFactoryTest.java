package com.fuma.hiselectors.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.kakao.config.KakaoMessageProperties;
import com.fuma.hiselectors.kakao.dto.DefaultTextTemplate;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextTemplateFactoryTest {

    private final TextTemplateFactory factory = new TextTemplateFactory(
            new KakaoMessageProperties("https://example.com", "https://m.example.com",
                    "https://example.com/image.png", ""),
            new NotificationMessageFactory());

    @Test
    @DisplayName("모든 알림 유형을 버튼이 포함된 TEXT 템플릿으로 생성한다")
    void allNotificationTypesHaveTextTemplate() {
        for (NotificationType type : NotificationType.values()) {
            var created = factory.create(new NotificationMessageCommand(
                    1L, 2L, 3L, "홍길동", "상세 안내", type));
            assertThat(created.template()).isInstanceOf(DefaultTextTemplate.class);
            DefaultTextTemplate template = (DefaultTextTemplate) created.template();

            assertThat(template.objectType()).isEqualTo("text");
            assertThat(template.text()).contains("홍길동").hasSizeLessThanOrEqualTo(200);
            assertThat(template.buttons()).singleElement()
                    .extracting(button -> button.title()).asString().isNotBlank();
            assertThat(created.body()).isEqualTo(template.text());
        }
    }
}
