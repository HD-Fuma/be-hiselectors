package com.fuma.hiselectors.purchase.service;

import com.fuma.hiselectors.purchase.dto.AuthenticatedPurchaseRequest;
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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final UserRepository userRepository;
    private final SelectorsRepository selectorsRepository;
    private final ProductRepository productRepository;
    private final SelectorAccessService selectorAccessService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PurchaseResponse purchase(PurchaseRequest request) {
        if (request.quantity() < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구매 수량은 1개 이상이어야 합니다.");
        }

        if (!userRepository.existsById(request.buyerUserId())) {
            throw new BusinessException(ErrorCode.PURCHASE_USER_NOT_FOUND);
        }

        Long selectorsId = findSelectorsId(request.selectorsCode());
        Product product = productRepository.findByProductCode(request.productCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.isAvailableForSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        validateProductPrice(product);

        return createPurchase(request, selectorsId, product);
    }

    @Transactional
    public PurchaseResponse purchase(String loginId, AuthenticatedPurchaseRequest request) {
        Long buyerUserId = userRepository.findByHiId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_USER_NOT_FOUND))
                .getId();
        return purchase(new PurchaseRequest(buyerUserId, request.selectorsCode(),
                request.productCode(), request.quantity()));
    }

    private Long findSelectorsId(String selectorsCode) {
        if (!StringUtils.hasText(selectorsCode)) {
            return null;
        }
        Selectors selectors = selectorsRepository.findBySelectorsCodeForUpdate(selectorsCode)
                .filter(value -> !value.isDeleted() && !value.isBlacklisted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        selectorAccessService.requireCurrent(selectors);
        return selectors.getId();
    }

    private PurchaseResponse createPurchase(
            PurchaseRequest request, Long selectorsId, Product product) {
        BigDecimal quantity = BigDecimal.valueOf(request.quantity());
        BigDecimal discountAmount = product.getRegularPrice()
                .subtract(product.getSalePrice())
                .multiply(quantity);
        BigDecimal paidAmount = product.getSalePrice().multiply(quantity);

        LocalDateTime purchasedAt = LocalDateTime.now();
        PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                .orderNo("TMP-" + UUID.randomUUID())
                .userId(request.buyerUserId())
                .selectorsId(selectorsId)
                .productId(product.getId())
                .quantity(request.quantity())
                .regularUnitPrice(product.getRegularPrice())
                .saleUnitPrice(product.getSalePrice())
                .discountAmount(discountAmount)
                .paidAmount(paidAmount)
                .purchasedAt(purchasedAt)
                .build();

        PurchaseHistory saved = purchaseHistoryRepository.saveAndFlush(purchaseHistory);
        String orderNo = "ORD" + purchasedAt.getYear() + String.format("%05d", saved.getId());
        saved.assignOrderNumber(orderNo);
        if (selectorsId != null) {
            eventPublisher.publishEvent(new PurchaseCreatedEvent(saved.getId(), selectorsId));
        }
        return PurchaseResponse.of(saved, PurchaseProcessingResult.CREATED);
    }

    private void validateProductPrice(Product product) {
        if (product.getRegularPrice() == null
                || product.getSalePrice() == null
                || product.getRegularPrice().signum() < 0
                || product.getSalePrice().signum() < 0
                || product.getSalePrice().compareTo(product.getRegularPrice()) > 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_PURCHASE_AMOUNT, "상품 가격 정보가 올바르지 않습니다.");
        }
    }
}
