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
                "셀렉터님이 소개한 상품에서 첫 주문이 들어왔어요. "
                        + "좋은 시작이에요. 앞으로의 활동도 응원하겠습니다.");
        assertThat(message.buttonTitle()).isEqualTo("성과 확인하기");
    }

    @Test
    void createsFirstRevenueMessage() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.FIRST_REVENUE, "셀렉터", "3,000");

        assertThat(message.title()).isEqualTo("[셀렉터스 첫 수익 안내]");
        assertThat(message.description()).isEqualTo(
                "셀렉터님, 첫 정산 대상 수익 3,000원이 확정되었어요. "
                        + "자세한 내용은 성과 페이지에서 확인해 주세요.");
        assertThat(message.buttonTitle()).isEqualTo("성과 확인하기");
    }

    @Test
    void createsSalesMilestoneMessage() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.SALES_1M, "셀렉터", "1,000,000");

        assertThat(message.title()).isEqualTo("[셀렉터스 누적 매출 안내]");
        assertThat(message.description()).isEqualTo(
                "셀렉터님, 누적 확정 매출이 1,000,000원을 달성했어요. "
                        + "꾸준히 상품을 소개해 주셔서 감사합니다.");
        assertThat(message.buttonTitle()).isEqualTo("성과 확인하기");
    }

    @Test
    void createsOrderMilestoneMessage() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.ORDERS_50, "셀렉터", "50");

        assertThat(message.title()).isEqualTo("[셀렉터스 누적 판매 안내]");
        assertThat(message.description()).isEqualTo(
                "셀렉터님, 누적 판매 50건을 달성했어요. "
                        + "소개해 주신 상품에 꾸준한 관심이 이어지고 있습니다.");
        assertThat(message.buttonTitle()).isEqualTo("성과 확인하기");
    }
}
