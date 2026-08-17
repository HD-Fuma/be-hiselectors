package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.KakaoTemplateType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
// TEXT, FEED, LIST 유형에 맞는 팩토리 선택
public class KakaoTemplateFactoryResolver {

    private final Map<KakaoTemplateType, KakaoTemplateFactory> factories;

    public KakaoTemplateFactoryResolver(List<KakaoTemplateFactory> factories) {
        this.factories = new EnumMap<>(KakaoTemplateType.class);
        for (KakaoTemplateFactory factory : factories) {
            KakaoTemplateFactory previous = this.factories.put(factory.templateType(), factory);
            if (previous != null) {
                throw new IllegalStateException(
                        "카카오 템플릿 팩토리가 중복 등록되었습니다: " + factory.templateType());
            }
        }
    }

    public CreatedKakaoTemplate create(KakaoTemplateType type,
                                       NotificationMessageCommand command) {
        KakaoTemplateFactory factory = factories.get(type);
        if (factory == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "지원하지 않는 카카오 메시지 템플릿 유형입니다: " + type);
        }
        return factory.create(command);
    }
}
