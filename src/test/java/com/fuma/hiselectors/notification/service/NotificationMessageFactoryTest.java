package com.fuma.hiselectors.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.notification.model.NotificationType;
import org.junit.jupiter.api.Test;

class NotificationMessageFactoryTest {

    private final NotificationMessageFactory factory = new NotificationMessageFactory();

    @Test
    void createsFirstPurchaseMessage() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.FIRST_PURCHASE, "셀렉터", null);

        assertThat(message.title()).isEqualTo("[셀렉터스 첫 구매 안내]");
        assertThat(message.description()).isEqualTo(
                "셀렉터님이 공유한 상품에서 첫 구매가 발생했어요. "
                        + "좋은 시작이에요. 앞으로의 활동도 응원하겠습니다.");
        assertThat(message.buttonTitle()).isEqualTo("성과 확인하기");
    }
}
