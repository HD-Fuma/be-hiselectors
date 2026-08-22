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

    @Test
    void createsLastMonthSalesSurpassedMessage() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.LAST_MONTH_SALES, "셀렉터", null);

        assertThat(message.title()).isEqualTo("[셀렉터스 매출 성장 안내]");
        assertThat(message.description()).isEqualTo(
                "셀렉터님, 이번 달 매출이 지난달 매출을 넘어섰어요. "
                        + "꾸준한 활동으로 좋은 흐름이 이어지고 있습니다.");
        assertThat(message.buttonTitle()).isEqualTo("성과 확인하기");
    }

    @Test
    void createsWeeklySalesGrowthMessage() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.WEEKLY_SALES_GROWTH, "셀렉터", "32");

        assertThat(message.title()).isEqualTo("[셀렉터스 주간 매출 안내]");
        assertThat(message.description()).isEqualTo(
                "셀렉터님, 지난주 매출이 전주보다 32% 증가했어요. "
                        + "지난 한 주도 꾸준히 활동해 주셔서 감사합니다.");
        assertThat(message.buttonTitle()).isEqualTo("성과 확인하기");
    }

    @Test
    void createsNewWeeklySalesMessageWhenPreviousWeekWasZero() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.WEEKLY_SALES_GROWTH, "셀렉터", null);

        assertThat(message.description()).isEqualTo(
                "셀렉터님, 지난주에 새로운 매출이 발생했어요. "
                        + "지난 한 주도 꾸준히 활동해 주셔서 감사합니다.");
    }

    @Test
    void createsMidMonthActivityMessage() {
        NotificationMessageFactory.MessageText message = factory.create(
                NotificationType.MID_MONTH_ACTIVITY, "셀렉터스", null);

        assertThat(message.title()).isEqualTo("[셀렉터스 활동 안내]");
        assertThat(message.description()).isEqualTo(
                "셀렉터스님, 이번 달에는 아직 새로운 구매가 발생하지 않았어요. "
                        + "부담 없이 이전에 반응이 좋았던 상품을 다시 소개해 보셔도 좋겠습니다.");
        assertThat(message.buttonTitle()).isEqualTo("상품 확인하기");
    }
}
