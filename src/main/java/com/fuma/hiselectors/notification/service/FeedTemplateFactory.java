package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.kakao.config.KakaoMessageProperties;
import com.fuma.hiselectors.kakao.dto.DefaultFeedTemplate;
import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import com.fuma.hiselectors.notification.service.NotificationMessageFactory.MessageText;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
// 이미지, 콘텐츠, 버튼이 포함된 DefaultFeedTemplate 생성
public class FeedTemplateFactory extends KakaoTemplateFactory {

    private final KakaoMessageProperties properties;

    public FeedTemplateFactory(KakaoMessageProperties properties,
                               NotificationMessageFactory messageFactory) {
        super(messageFactory);
        this.properties = properties;
    }

    @Override
    public KakaoTemplateType templateType() {
        return KakaoTemplateType.FEED;
    }

    @Override
    protected KakaoMessageTemplate createTemplate(MessageText message, String body) {
        DefaultFeedTemplate.Link link = new DefaultFeedTemplate.Link(
                properties.webUrl(), properties.mobileWebUrl());
        return new DefaultFeedTemplate(
                "feed",
                new DefaultFeedTemplate.Content(message.title(), message.description(),
                        properties.imageUrl(), link),
                List.of(new DefaultFeedTemplate.Button(message.buttonTitle(), link))
        );
    }
}
