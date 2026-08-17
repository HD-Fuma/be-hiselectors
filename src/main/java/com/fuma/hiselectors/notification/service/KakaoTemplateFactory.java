package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import com.fuma.hiselectors.notification.service.NotificationMessageFactory.MessageText;

// 수신자 이름 처리, 메시지 문구 생성, 제목·본문 결합 등 공통 생성 흐름 담당
public abstract class KakaoTemplateFactory {

    private final NotificationMessageFactory messageFactory;

    protected KakaoTemplateFactory(NotificationMessageFactory messageFactory) {
        this.messageFactory = messageFactory;
    }

    public final CreatedKakaoTemplate create(NotificationMessageCommand command) {
        String name = command.receiverName() == null || command.receiverName().isBlank()
                ? "회원" : command.receiverName();
        MessageText message = messageFactory.create(
                command.notificationType(), name, command.detail());
        String body = message.title() + "\n\n" + message.description();

        return new CreatedKakaoTemplate(createTemplate(message, body), body);
    }

    public abstract KakaoTemplateType templateType();

    // 하위 팩토리가 실제 카카오 DTO 생성 방법을 결정하도록 하는 추상메서드
    protected abstract KakaoMessageTemplate createTemplate(MessageText message, String body);
}
