package com.fuma.hiselectors.performance.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.notification.dto.NotificationMessageCommand;
import com.fuma.hiselectors.notification.model.NotificationType;
import com.fuma.hiselectors.notification.repository.NotificationRepository;
import com.fuma.hiselectors.notification.service.NotificationService;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.service.CommissionRateCalculator;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PerformanceNotificationServiceTest {

    private SelectorsRepository selectorsRepository;
    private NotificationRepository notificationRepository;
    private NotificationService notificationService;
    private PurchaseHistoryRepository purchaseHistoryRepository;
    private ApplicationRepository applicationRepository;
    private CommissionRateCalculator commissionRateCalculator;
    private PerformanceNotificationService service;
    private Selectors selectors;

    @BeforeEach
    void setUp() {
        selectorsRepository = mock(SelectorsRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        notificationService = mock(NotificationService.class);
        purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        commissionRateCalculator = mock(CommissionRateCalculator.class);
        service = new PerformanceNotificationService(
                selectorsRepository, notificationRepository, notificationService,
                purchaseHistoryRepository, applicationRepository, commissionRateCalculator);
        ReflectionTestUtils.setField(service, "senderAdminLoginId", "sender-admin");

        selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(2L);
        when(selectors.getUserId()).thenReturn(20L);
        when(selectors.getApplicationId()).thenReturn(30L);
        when(selectors.getSelectorsNickname()).thenReturn("셀렉터");
        when(selectorsRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(selectors));

        Application application = mock(Application.class);
        when(application.getSnsCode()).thenReturn(SnsPlatform.INSTAGRAM);
        when(application.getFollowerCount()).thenReturn(1_000L);
        when(applicationRepository.findById(30L)).thenReturn(Optional.of(application));
        when(commissionRateCalculator.calculate(SnsPlatform.INSTAGRAM, 1_000L))
                .thenReturn(new BigDecimal("3.00"));
    }

    @Test
    void sendsFirstPurchaseNotificationOnce() {
        service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L));

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriend(eq("sender-admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.recipientUserId()).isEqualTo(20L);
        org.assertj.core.api.Assertions.assertThat(command.referenceId()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(command.receiverName()).isEqualTo("셀렉터");
        org.assertj.core.api.Assertions.assertThat(command.notificationType())
                .isEqualTo(NotificationType.FIRST_PURCHASE);
    }

    @Test
    void sendsOnlyOnceWhenPurchaseEventIsRepeated() {
        when(notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.FIRST_PURCHASE.getPurposeCode(), 2L))
                .thenReturn(0L, 1L);

        service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L));
        service.handlePurchaseCreated(new PurchaseCreatedEvent(102L, 2L));

        verify(notificationService).sendToFriend(any(), any());
    }

    @Test
    void skipsWhenSenderIsNotConfigured() {
        ReflectionTestUtils.setField(service, "senderAdminLoginId", " ");

        service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L));

        verify(selectorsRepository, never()).findByIdForUpdate(any());
        verify(notificationService, never()).sendToFriend(any(), any());
    }

    @Test
    void doesNotPropagateKakaoFailure() {
        when(notificationService.sendToFriend(any(), any()))
                .thenThrow(new IllegalStateException("kakao failed"));

        assertThatCode(() -> service.handlePurchaseCreated(new PurchaseCreatedEvent(101L, 2L)))
                .doesNotThrowAnyException();
    }

    @Test
    void sendsFirstRevenueWhenConfirmedCommissionBecomesPositive() {
        when(purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                2L, PurchaseStatus.PURCHASE_CONFIRMED)).thenReturn(new BigDecimal("100000"));

        service.notifyFirstRevenue(2L);

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriend(eq("sender-admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.referenceId()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(command.detail()).isEqualTo("3,000");
        org.assertj.core.api.Assertions.assertThat(command.notificationType())
                .isEqualTo(NotificationType.FIRST_REVENUE);
    }

    @Test
    void skipsFirstRevenueWhenRoundedCommissionIsZero() {
        when(purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                2L, PurchaseStatus.PURCHASE_CONFIRMED)).thenReturn(BigDecimal.ONE);

        service.notifyFirstRevenue(2L);

        verify(notificationService, never()).sendToFriend(any(), any());
    }

    @Test
    void sendsFirstRevenueOnlyOnce() {
        when(purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                2L, PurchaseStatus.PURCHASE_CONFIRMED)).thenReturn(new BigDecimal("10000"));
        when(notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.FIRST_REVENUE.getPurposeCode(), 2L)).thenReturn(0L, 1L);

        service.notifyFirstRevenue(2L);
        service.notifyFirstRevenue(2L);

        verify(notificationService).sendToFriend(any(), any());
    }

    @Test
    void sendsOnlyHighestReachedSalesMilestone() {
        when(purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                2L, PurchaseStatus.PURCHASE_CONFIRMED)).thenReturn(new BigDecimal("1200000"));

        service.notifySalesMilestone(2L);

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriend(eq("sender-admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.referenceId()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(command.detail()).isEqualTo("1,000,000");
        org.assertj.core.api.Assertions.assertThat(command.notificationType())
                .isEqualTo(NotificationType.SALES_1M);
    }

    @Test
    void skipsSalesMilestoneBelowFirstThreshold() {
        when(purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                2L, PurchaseStatus.PURCHASE_CONFIRMED)).thenReturn(new BigDecimal("99999"));

        service.notifySalesMilestone(2L);

        verify(notificationService, never()).sendToFriend(any(), any());
    }

    @Test
    void doesNotBackfillLowerSalesMilestone() {
        when(purchaseHistoryRepository.sumPaidAmountBySelectorsIdAndStatus(
                2L, PurchaseStatus.PURCHASE_CONFIRMED)).thenReturn(new BigDecimal("1200000"));
        when(notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.SALES_1M.getPurposeCode(), 2L)).thenReturn(1L);

        service.notifySalesMilestone(2L);

        verify(notificationService, never()).sendToFriend(any(), any());
    }

    @Test
    void sendsOnlyHighestReachedOrderMilestone() {
        when(purchaseHistoryRepository.countDistinctOrdersBySelectorsIdAndStatusIn(
                2L, java.util.List.of(PurchaseStatus.PURCHASE_CONFIRMED))).thenReturn(67L);

        service.notifyOrderMilestone(2L);

        ArgumentCaptor<NotificationMessageCommand> commandCaptor =
                ArgumentCaptor.forClass(NotificationMessageCommand.class);
        verify(notificationService).sendToFriend(eq("sender-admin"), commandCaptor.capture());
        NotificationMessageCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.referenceId()).isEqualTo(2L);
        org.assertj.core.api.Assertions.assertThat(command.detail()).isEqualTo("50");
        org.assertj.core.api.Assertions.assertThat(command.notificationType())
                .isEqualTo(NotificationType.ORDERS_50);
    }

    @Test
    void skipsOrderMilestoneBelowFirstThreshold() {
        when(purchaseHistoryRepository.countDistinctOrdersBySelectorsIdAndStatusIn(
                2L, java.util.List.of(PurchaseStatus.PURCHASE_CONFIRMED))).thenReturn(9L);

        service.notifyOrderMilestone(2L);

        verify(notificationService, never()).sendToFriend(any(), any());
    }

    @Test
    void doesNotBackfillLowerOrderMilestone() {
        when(purchaseHistoryRepository.countDistinctOrdersBySelectorsIdAndStatusIn(
                2L, java.util.List.of(PurchaseStatus.PURCHASE_CONFIRMED))).thenReturn(67L);
        when(notificationRepository.countByNotificationPurposeCodeAndReferenceId(
                NotificationType.ORDERS_50.getPurposeCode(), 2L)).thenReturn(1L);

        service.notifyOrderMilestone(2L);

        verify(notificationService, never()).sendToFriend(any(), any());
    }
}
