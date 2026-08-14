package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.config.KakaoMessageProperties;
import com.fuma.hiselectors.kakao.dto.DefaultTextTemplate;
import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import com.fuma.hiselectors.notification.service.NotificationMessageFactory.MessageText;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
// DefaultTextTemplate 생성 및 200자 제한 검증
public class TextTemplateFactory extends KakaoTemplateFactory {

    private static final int MAX_TEXT_LENGTH = 200;

    private final KakaoMessageProperties properties;

    public TextTemplateFactory(KakaoMessageProperties properties,
                               NotificationMessageFactory messageFactory) {
        super(messageFactory);
        this.properties = properties;
    }

    @Override
    public KakaoTemplateType templateType() {
        return KakaoTemplateType.TEXT;
    }

    @Override
    protected KakaoMessageTemplate createTemplate(MessageText message, String body) {
        validateText(body);

        DefaultTextTemplate.Link link = new DefaultTextTemplate.Link(
                properties.webUrl(), properties.mobileWebUrl());
        return new DefaultTextTemplate(
                "text",
                body,
                link,
                List.of(new DefaultTextTemplate.Button(message.buttonTitle(), link))
        );
    }

    private void validateText(String text) {
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "카카오 텍스트 메시지는 200자를 초과할 수 없습니다.");
        }
    }
}
