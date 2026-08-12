package com.fuma.hiselectors.purchase.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.purchase.dto.PurchaseRequest;
import com.fuma.hiselectors.purchase.dto.PurchaseResponse;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseProcessingResult;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selector;
import com.fuma.hiselectors.selectors.repository.SelectorRepository;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final UserRepository userRepository;
    private final SelectorRepository selectorRepository;
    private final ProductRepository productRepository;

    @Transactional
    public PurchaseResponse purchase(PurchaseRequest request) {
        if (request.quantity() < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "구매 수량은 1개 이상이어야 합니다.");
        }

        if (!userRepository.existsById(request.buyerUserId())) {
            throw new BusinessException(ErrorCode.PURCHASE_USER_NOT_FOUND);
        }

        Long selectorId = findSelectorId(request.selectorCode());
        Product product = productRepository.findByProductCode(request.productCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.isAvailableForSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
        validateProductPrice(product);

        return purchaseHistoryRepository.findByOrderNoAndProductIdForUpdate(
                        request.orderNo(), product.getId())
                .map(existing -> handleExistingPurchase(existing, request, selectorId))
                .orElseGet(() -> createPurchase(request, selectorId, product));
    }

    private Long findSelectorId(String selectorCode) {
        if (!StringUtils.hasText(selectorCode)) {
            return null;
        }
        Selector selector = selectorRepository.findBySelectorsCode(selectorCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        return selector.getId();
    }

    private PurchaseResponse createPurchase(
            PurchaseRequest request, Long selectorId, Product product) {
        BigDecimal quantity = BigDecimal.valueOf(request.quantity());
        BigDecimal discountAmount = product.getRegularPrice()
                .subtract(product.getSalePrice())
                .multiply(quantity);
        BigDecimal paidAmount = product.getSalePrice().multiply(quantity);

        PurchaseHistory purchaseHistory = PurchaseHistory.builder()
                .orderNo(request.orderNo())
                .userId(request.buyerUserId())
                .selectorId(selectorId)
                .productId(product.getId())
                .quantity(request.quantity())
                .regularUnitPrice(product.getRegularPrice())
                .saleUnitPrice(product.getSalePrice())
                .discountAmount(discountAmount)
                .paidAmount(paidAmount)
                .purchasedAt(LocalDateTime.now())
                .build();

        PurchaseHistory saved = purchaseHistoryRepository.save(purchaseHistory);
        return PurchaseResponse.of(saved, PurchaseProcessingResult.CREATED);
    }

    private PurchaseResponse handleExistingPurchase(
            PurchaseHistory existing, PurchaseRequest request, Long selectorId) {
        if (existing.hasSamePurchaseIdentity(
                request.buyerUserId(), selectorId, request.quantity())) {
            return PurchaseResponse.of(existing, PurchaseProcessingResult.ALREADY_PROCESSED);
        }
        throw new BusinessException(ErrorCode.PURCHASE_CONFLICT);
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
