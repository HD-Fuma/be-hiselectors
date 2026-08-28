package com.fuma.hiselectors.notification.service;

import com.fuma.hiselectors.notification.model.NotificationType;
import org.springframework.stereotype.Component;

@Component
// 문구 생성기: 알림 목적별 제목·본문·버튼 이름 결정
public class NotificationMessageFactory {

    public MessageText create(NotificationType type, String name, String detail) {
        return switch (type) {
            case APPLICATION_RECEIVED -> applicationSubmitted(name);
            case SELECTION_APPROVED -> selectionApproved(name);
            case SELECTION_REJECTED -> selectionRejected(name, detail);
            case CONTENT_EDIT_REQUEST -> contentEditRequest(name, detail);
            case CONTENT_EDIT_DONE -> contentEditDone(name);
            case DEAD_LINK_NOTICE -> deadLinkNotice(name, detail);
            case FIRST_PURCHASE -> firstPurchase(name);
            case FIRST_REVENUE -> firstRevenue(name, detail);
            case LAST_MONTH_SALES -> lastMonthSales(name);
            case MID_MONTH_ACTIVITY -> midMonthActivity(name);
            case NO_PAGE_VIEWS -> noPageViews(name);
            case SALES_100K, SALES_500K, SALES_1M, SALES_5M, SALES_10M ->
                    salesMilestone(name, detail);
            case ORDERS_10, ORDERS_50, ORDERS_100 -> orderMilestone(name, detail);
            case PENALTY_RELEASED -> penaltyReleased(name);
            case SETTLEMENT_COMPLETED -> settlementCompleted(name, detail);
            case SETTLEMENT_CARRYOVER -> settlementCarryover(name, detail);
            case SETTLEMENT_MISSING -> settlementMissing(name);
            case SETTLEMENT_UPCOMING -> settlementUpcoming(name, detail);
            case WEEKLY_SALES_GROWTH -> weeklySalesGrowth(name, detail);
            case ACTIVITY_GUIDE -> activityGuide(name, detail);
        };
    }

    private MessageText applicationSubmitted(String name) {
        return new MessageText(
                "[셀렉터스 지원 접수 안내]",
                name + "님, 셀렉터스 지원이 정상적으로 접수되었습니다.\n\n"
                        + "제출해 주신 내용을 바탕으로 심사가 진행되며, "
                        + "결과는 다시 안내드리겠습니다.\n\n"
                        + "셀렉터스에 지원해 주셔서 감사합니다.",
                "더현대Hi 바로가기"
        );
    }

    private MessageText selectionApproved(String name) {
        return new MessageText(
                "[셀렉터스 가입 승인 안내]",
                name + "님, 셀렉터스 가입 심사가 완료되었습니다.\n\n"
                        + "심사 결과, 셀렉터스로 최종 선정되었습니다.\n\n"
                        + "이제 셀렉터스 활동을 시작하실 수 있습니다. "
                        + "활동 전 가이드와 유의사항을 확인해 주세요.",
                "활동 가이드 확인하기"
        );
    }

    private MessageText selectionRejected(String name, String detail) {
        return new MessageText(
                "[셀렉터스 가입 반려 안내]",
                name + "님, 셀렉터스 가입 심사가 완료되었습니다.\n\n"
                        + "내부 기준에 따라 검토한 결과, "
                        + "아쉽게도 이번 신청 건은 승인되지 않았습니다."
                        + suffix(detail)
                        + "\n\n앞으로 콘텐츠 활동을 지속해 주신다면 "
                        + "추후 다시 지원하셨을 때 긍정적으로 검토하겠습니다.\n\n"
                        + "셀렉터스 서비스에 관심을 가지고 신청해주셔서 감사합니다.",
                "더현대Hi 바로가기"
        );
    }

    private MessageText contentEditRequest(String name, String detail) {
        return new MessageText(
                "[셀렉터스 콘텐츠 수정 요청 안내]",
                name + "님이 등록하신 콘텐츠 검수 결과, "
                        + "수정이 필요한 사항이 확인되었습니다."
                        + suffix(detail)
                        + "\n\n내용을 확인하신 후 콘텐츠를 수정해 주세요. "
                        + "수정된 콘텐츠는 다시 검수됩니다.",
                "콘텐츠 확인하기"
        );
    }

    private MessageText contentEditDone(String name) {
        return new MessageText(
                "[셀렉터스 콘텐츠 수정 확인 안내]",
                name + "님이 수정하신 콘텐츠의 재검수가 완료되었습니다.\n\n"
                        + "요청드린 수정 사항이 정상적으로 반영된 것을 확인했습니다.\n\n"
                        + "해당 콘텐츠는 현재 정상 상태로 처리되었습니다.\n\n"
                        + "협조해 주셔서 감사합니다.",
                "콘텐츠 확인하기"
        );
    }

    private MessageText penaltyReleased(String name) {
        return new MessageText(
                "[셀렉터스 패널티 해제 안내]",
                name + "님의 패널티가 해제되었습니다.\n\n"
                        + "현재 적용 중이던 패널티 주기가 종료되었습니다.\n\n"
                        + "앞으로도 활동 가이드와 유의사항을 확인해 주세요.",
                "활동 가이드 확인하기"
        );
    }

    private MessageText deadLinkNotice(String name, String detail) {
        return new MessageText(
                "[셀렉터스 링크 확인 요청]",
                name + "님이 등록한 셀렉터스 콘텐츠에서 "
                        + "정상적으로 연결되지 않는 링크가 확인되었습니다."
                        + suffix(detail)
                        + "\n\n해당 링크를 확인하신 후 정상적인 셀렉터스 링크로 수정해 주세요.\n\n"
                        + "링크가 정상적으로 연결되지 않을 경우 "
                        + "클릭 및 구매 성과가 정상적으로 집계되지 않을 수 있습니다.",
                "콘텐츠 확인하기"
        );
    }

    private MessageText settlementMissing(String name) {
        return new MessageText(
                "[셀렉터스 정산 정보 등록 안내]",
                name + "님, 현재 셀렉터스 정산 정보가 등록되지 않은 상태입니다.\n\n"
                        + "정산 정보가 등록되지 않으면 발생한 수익에 대한 "
                        + "정산이 진행되지 않습니다.\n\n"
                        + "원활한 수익 지급을 위해 정산 정보를 등록해 주세요.\n\n"
                        + "※ 가입 후 12개월 이상 정산 정보 미등록 또는 "
                        + "오기재 상태가 지속될 경우 정산금이 소멸될 수 있습니다.",
                "정산 정보 등록하기"
        );
    }

    private MessageText settlementUpcoming(String name, String detail) {
        return new MessageText(
                "[셀렉터스 정산 예정 안내]",
                name + "님, " + detail + " 등록된 정산 정보를 미리 확인해 주세요.",
                "정산 내역 확인하기"
        );
    }

    private MessageText settlementCompleted(String name, String detail) {
        return new MessageText(
                "[셀렉터스 정산 완료 안내]",
                name + "님, " + detail + " 자세한 내용은 정산 내역에서 확인해 주세요.",
                "정산 내역 확인하기"
        );
    }

    private MessageText settlementCarryover(String name, String detail) {
        return new MessageText(
                "[셀렉터스 정산 이월 안내]",
                name + "님, " + detail + " 자세한 내용은 정산 내역에서 확인해 주세요.",
                "정산 내역 확인하기"
        );
    }

    private MessageText firstPurchase(String name) {
        return new MessageText(
                "[셀렉터스 첫 구매 안내]",
                name + "님이 소개한 상품에서 첫 주문이 들어왔어요. "
                        + "좋은 시작이에요. 앞으로의 활동도 응원하겠습니다.",
                "성과 확인하기"
        );
    }

    private MessageText firstRevenue(String name, String revenue) {
        return new MessageText(
                "[셀렉터스 첫 수익 안내]",
                name + "님, 첫 정산 대상 수익 " + revenue + "원이 확정되었어요. "
                        + "자세한 내용은 성과 페이지에서 확인해 주세요.",
                "성과 확인하기"
        );
    }

    private MessageText lastMonthSales(String name) {
        return new MessageText(
                "[셀렉터스 매출 성장 안내]",
                name + "님, 이번 달 매출이 지난달 매출을 넘어섰어요. "
                        + "꾸준한 활동으로 좋은 흐름이 이어지고 있습니다.",
                "성과 확인하기"
        );
    }

    private MessageText weeklySalesGrowth(String name, String increaseRate) {
        String result = increaseRate == null || increaseRate.isBlank()
                ? "지난주에 새로운 매출이 발생했어요. "
                : "지난주 매출이 전주보다 " + increaseRate + "% 증가했어요. ";
        return new MessageText(
                "[셀렉터스 주간 매출 안내]",
                name + "님, " + result
                        + "지난 한 주도 꾸준히 활동해 주셔서 감사합니다.",
                "성과 확인하기"
        );
    }

    private MessageText midMonthActivity(String name) {
        return new MessageText(
                "[셀렉터스 활동 안내]",
                name + "님, 이번 달에는 아직 새로운 구매가 발생하지 않았어요. "
                        + "부담 없이 이전에 반응이 좋았던 상품을 다시 소개해 보셔도 좋겠습니다.",
                "상품 확인하기"
        );
    }

    private MessageText noPageViews(String name) {
        return new MessageText(
                "[셀렉터스 페이지 확인 안내]",
                name + "님, 아직 셀렉터스 페이지의 조회 기록이 없어요. "
                        + "활동 중이라면 페이지가 정상적으로 열리는지 한 번 확인해 주세요.",
                "페이지 확인하기"
        );
    }

    private MessageText salesMilestone(String name, String sales) {
        return new MessageText(
                "[셀렉터스 누적 매출 안내]",
                name + "님, 누적 확정 매출이 " + sales + "원을 달성했어요. "
                        + "꾸준히 상품을 소개해 주셔서 감사합니다.",
                "성과 확인하기"
        );
    }

    private MessageText orderMilestone(String name, String orders) {
        return new MessageText(
                "[셀렉터스 누적 판매 안내]",
                name + "님, 누적 판매 " + orders + "건을 달성했어요. "
                        + "소개해 주신 상품에 꾸준한 관심이 이어지고 있습니다.",
                "성과 확인하기"
        );
    }

    private MessageText activityGuide(String name, String detail) {
        return new MessageText(
                "[셀렉터스 활동 안내]",
                name + "님, 셀렉터스 활동 가이드를 확인하고 활동에 참여해 주세요."
                        + suffix(detail),
                "활동 가이드 확인하기"
        );
    }

    private String suffix(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }

        return "\n\n■ 상세 내용\n" + detail;
    }

    public record MessageText(String title, String description, String buttonTitle) {
    }
}
