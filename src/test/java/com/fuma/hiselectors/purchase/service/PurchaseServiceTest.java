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
import com.fuma.hiselectors.purchase.dto.PurchaseRequest;
import com.fuma.hiselectors.purchase.dto.PurchaseResponse;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseProcessingResult;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selector;
import com.fuma.hiselectors.selectors.repository.SelectorRepository;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PurchaseServiceTest {

    private PurchaseHistoryRepository purchaseHistoryRepository;
    private UserRepository userRepository;
    private SelectorRepository selectorRepository;
    private ProductRepository productRepository;
    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        purchaseHistoryRepository = mock(PurchaseHistoryRepository.class);
        userRepository = mock(UserRepository.class);
        selectorRepository = mock(SelectorRepository.class);
        productRepository = mock(ProductRepository.class);
        purchaseService = new PurchaseService(
                purchaseHistoryRepository, userRepository, selectorRepository, productRepository);
    }

    @Test
    void createsPurchaseUsingCurrentProductPrices() {
        givenReferences(new BigDecimal("10000"), new BigDecimal("8000"));
        when(purchaseHistoryRepository.findByOrderNoAndProductIdForUpdate("ORDER-1", 3L))
                .thenReturn(Optional.empty());
        when(purchaseHistoryRepository.save(any(PurchaseHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseResponse response = purchaseService.purchase(request());

        assertThat(response.processingResult()).isEqualTo(PurchaseProcessingResult.CREATED);
        assertThat(response.status()).isEqualTo(PurchaseStatus.PURCHASED);
        assertThat(response.regularUnitPrice()).isEqualByComparingTo("10000");
        assertThat(response.saleUnitPrice()).isEqualByComparingTo("8000");
        assertThat(response.discountAmount()).isEqualByComparingTo("4000");
        assertThat(response.paidAmount()).isEqualByComparingTo("16000");
    }

    @Test
    void createsPurchaseWithoutSelectorCode() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(3L);
        when(product.getRegularPrice()).thenReturn(new BigDecimal("10000"));
        when(product.getSalePrice()).thenReturn(new BigDecimal("8000"));
        when(product.isAvailableForSale()).thenReturn(true);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findByProductCode("PRODUCT-1"))
                .thenReturn(Optional.of(product));
        when(purchaseHistoryRepository.findByOrderNoAndProductIdForUpdate("ORDER-1", 3L))
                .thenReturn(Optional.empty());
        when(purchaseHistoryRepository.save(any(PurchaseHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseResponse response = purchaseService.purchase(
                new PurchaseRequest("ORDER-1", 1L, null, "PRODUCT-1", 2));

        assertThat(response.processingResult()).isEqualTo(PurchaseProcessingResult.CREATED);
        verify(selectorRepository, never()).findBySelectorsCode(any());
    }

    @Test
    void returnsOriginalSnapshotForSamePurchaseRequest() {
        givenReferences(new BigDecimal("12000"), new BigDecimal("9000"));
        PurchaseHistory existing = purchase();
        when(purchaseHistoryRepository.findByOrderNoAndProductIdForUpdate("ORDER-1", 3L))
                .thenReturn(Optional.of(existing));

        PurchaseResponse response = purchaseService.purchase(request());

        assertThat(response.processingResult()).isEqualTo(PurchaseProcessingResult.ALREADY_PROCESSED);
        assertThat(response.regularUnitPrice()).isEqualByComparingTo("10000");
        assertThat(response.saleUnitPrice()).isEqualByComparingTo("8000");
        verify(purchaseHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownBuyer() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> purchaseService.purchase(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PURCHASE_USER_NOT_FOUND);
        verify(purchaseHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidProductPrice() {
        givenReferences(new BigDecimal("8000"), new BigDecimal("10000"));

        assertThatThrownBy(() -> purchaseService.purchase(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PURCHASE_AMOUNT);
        verify(purchaseHistoryRepository, never()).save(any());
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

    private void givenReferences(BigDecimal regularPrice, BigDecimal salePrice) {
        Selector selector = mock(Selector.class);
        Product product = mock(Product.class);
        when(selector.getId()).thenReturn(2L);
        when(product.getId()).thenReturn(3L);
        when(product.getRegularPrice()).thenReturn(regularPrice);
        when(product.getSalePrice()).thenReturn(salePrice);
        when(product.isAvailableForSale()).thenReturn(true);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(selectorRepository.findBySelectorsCode("SELECTOR-1"))
                .thenReturn(Optional.of(selector));
        when(productRepository.findByProductCode("PRODUCT-1"))
                .thenReturn(Optional.of(product));
    }

    private PurchaseRequest request() {
        return new PurchaseRequest("ORDER-1", 1L, "SELECTOR-1", "PRODUCT-1", 2);
    }

    private PurchaseHistory purchase() {
        return PurchaseHistory.builder()
                .orderNo("ORDER-1")
                .userId(1L)
                .selectorId(2L)
                .productId(3L)
                .quantity(2)
                .regularUnitPrice(new BigDecimal("10000"))
                .saleUnitPrice(new BigDecimal("8000"))
                .discountAmount(new BigDecimal("4000"))
                .paidAmount(new BigDecimal("16000"))
                .purchasedAt(LocalDateTime.of(2026, 8, 11, 10, 0))
                .build();
    }
}
