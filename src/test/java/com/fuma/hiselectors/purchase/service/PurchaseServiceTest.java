package com.fuma.hiselectors.purchase.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.performance.notification.PurchaseCreatedEvent;
import com.fuma.hiselectors.purchase.dto.PurchaseRequest;
import com.fuma.hiselectors.purchase.dto.PurchaseResponse;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseProcessingResult;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class PurchaseServiceTest {

    private PurchaseHistoryRepository purchaseHistoryRepository;
    private UserRepository userRepository;
    private SelectorsRepository selectorsRepository;
    private ProductRepository productRepository;
    private SelectorAccessService selectorAccessService;
    private ApplicationEventPublisher eventPublisher;
    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        selectorsRepository = mock(SelectorsRepository.class);
        productRepository = mock(ProductRepository.class);
        selectorAccessService = mock(SelectorAccessService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        purchaseService = new PurchaseService(
                purchaseHistoryRepository, userRepository, selectorsRepository, productRepository,
                selectorAccessService, eventPublisher);
    }

    @Test
    void createsPurchaseUsingServerGeneratedOrderNumberAndProductPrices() {
        givenReferences(new BigDecimal("10000"), new BigDecimal("8000"));
        givenSavedPurchaseId(101L);

        PurchaseResponse response = purchaseService.purchase(request());

        assertThat(response.orderNo()).startsWith("ORD").endsWith("00101");
        assertThat(response.processingResult()).isEqualTo(PurchaseProcessingResult.CREATED);
        assertThat(response.discountAmount()).isEqualByComparingTo("4000");
        assertThat(response.paidAmount()).isEqualByComparingTo("16000");

        ArgumentCaptor<PurchaseCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(PurchaseCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().purchaseId()).isEqualTo(101L);
        assertThat(eventCaptor.getValue().selectorsId()).isEqualTo(2L);
    }

    @Test
    void createsPurchaseWithoutSelectorCode() {
        Product product = availableProduct(new BigDecimal("10000"), new BigDecimal("8000"));
        when(userRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findByProductCode("PRODUCT-1")).thenReturn(Optional.of(product));
        givenSavedPurchaseId(102L);

        purchaseService.purchase(new PurchaseRequest(1L, null, "PRODUCT-1", 2));

        verify(selectorsRepository, never()).findBySelectorsCode(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsUnknownBuyer() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> purchaseService.purchase(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PURCHASE_USER_NOT_FOUND);
    }

    @Test
    void rejectsInvalidProductPrice() {
        givenReferences(new BigDecimal("8000"), new BigDecimal("10000"));

        assertThatThrownBy(() -> purchaseService.purchase(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PURCHASE_AMOUNT);
    }

    @Test
    void rejectsUnavailableProduct() {
        givenReferences(new BigDecimal("10000"), new BigDecimal("8000"));
        Product product = productRepository.findByProductCode("PRODUCT-1").orElseThrow();
        when(product.isAvailableForSale()).thenReturn(false);

        assertThatThrownBy(() -> purchaseService.purchase(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_AVAILABLE);
        verify(purchaseHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsPurchaseAttributionToBlacklistedSelector() {
        Selectors selectors = mock(Selectors.class);
        when(selectors.isBlacklisted()).thenReturn(true);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(selectorsRepository.findBySelectorsCode("SELECTOR-1"))
                .thenReturn(Optional.of(selectors));

        assertThatThrownBy(() -> purchaseService.purchase(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SELECTOR_NOT_FOUND);

        verify(productRepository, never()).findByProductCode(any());
        verify(purchaseHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsPurchaseAttributionToPreviousSelector() {
        Selectors selectors = mock(Selectors.class);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(selectorsRepository.findBySelectorsCode("SELECTOR-1"))
                .thenReturn(Optional.of(selectors));
        when(selectorAccessService.requireCurrent(selectors))
                .thenThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        assertThatThrownBy(() -> purchaseService.purchase(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(productRepository, never()).findByProductCode(any());
    }

    private void givenReferences(BigDecimal regularPrice, BigDecimal salePrice) {
        Selectors selectors = mock(Selectors.class);
        Product product = availableProduct(regularPrice, salePrice);
        when(selectors.getId()).thenReturn(2L);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(selectorsRepository.findBySelectorsCode("SELECTOR-1"))
                .thenReturn(Optional.of(selectors));
        when(productRepository.findByProductCode("PRODUCT-1"))
                .thenReturn(Optional.of(product));
    }

    private Product availableProduct(BigDecimal regularPrice, BigDecimal salePrice) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(3L);
        when(product.getRegularPrice()).thenReturn(regularPrice);
        when(product.getSalePrice()).thenReturn(salePrice);
        when(product.isAvailableForSale()).thenReturn(true);
        return product;
    }

    private PurchaseRequest request() {
        return new PurchaseRequest(1L, "SELECTOR-1", "PRODUCT-1", 2);
    }

    private void givenSavedPurchaseId(Long id) {
        when(purchaseHistoryRepository.saveAndFlush(any(PurchaseHistory.class)))
                .thenAnswer(invocation -> {
                    PurchaseHistory purchase = invocation.getArgument(0);
                    ReflectionTestUtils.setField(purchase, "id", id);
                    return purchase;
                });
    }
}
