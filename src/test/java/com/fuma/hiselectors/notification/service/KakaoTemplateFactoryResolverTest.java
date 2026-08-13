package com.fuma.hiselectors.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.kakao.config.KakaoMessageProperties;
import com.fuma.hiselectors.kakao.dto.DefaultFeedTemplate;
import com.fuma.hiselectors.kakao.dto.DefaultTextTemplate;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import com.fuma.hiselectors.notification.model.NotificationType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoTemplateFactoryResolverTest {

    private final NotificationMessageCommand command = new NotificationMessageCommand(
            1L, 2L, 3L, "홍길동", null, NotificationType.SELECTION_APPROVED);
    private final KakaoTemplateFactoryResolver resolver;

    KakaoTemplateFactoryResolverTest() {
        KakaoMessageProperties properties = new KakaoMessageProperties(
                "https://example.com", "https://m.example.com",
                "https://example.com/image.png");
        NotificationMessageFactory messageFactory = new NotificationMessageFactory();
        resolver = new KakaoTemplateFactoryResolver(List.of(
                new TextTemplateFactory(properties, messageFactory),
                new FeedTemplateFactory(properties, messageFactory)
        ));
    }

    @Test
    @DisplayName("템플릿 유형에 맞는 팩토리를 선택한다")
    void resolveByTemplateType() {
        assertThat(resolver.create(KakaoTemplateType.TEXT, command).template())
                .isInstanceOf(DefaultTextTemplate.class);
        assertThat(resolver.create(KakaoTemplateType.FEED, command).template())
                .isInstanceOf(DefaultFeedTemplate.class);
    }

    @Test
    @DisplayName("구현되지 않은 리스트 템플릿은 명확한 오류를 반환한다")
    void rejectUnsupportedTemplateType() {
        assertThatThrownBy(() -> resolver.create(KakaoTemplateType.LIST, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("지원하지 않는 카카오 메시지 템플릿 유형");
    }
}
