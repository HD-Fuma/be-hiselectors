package com.fuma.hiselectors.analytics.service;

import com.fuma.hiselectors.analytics.dto.ViewLogRequest;
import com.fuma.hiselectors.analytics.dto.ViewLogResponse;
import com.fuma.hiselectors.analytics.model.ClickLog;
import com.fuma.hiselectors.analytics.repository.ClickLogRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupItemRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ViewLogService {

    private final ClickLogRepository clickLogRepository;
    private final SelectorsRepository selectorsRepository;
    private final ProductGroupRepository groupRepository;
    private final ProductGroupItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public ViewLogResponse record(String loginId, ViewLogRequest request) {
        Selectors selectors = selectorsRepository.findBySelectorsCode(request.selectorsCode())
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        Long referenceId = validateReference(selectors.getId(), request);
        Long viewerUserId = loginId == null ? null
                : userRepository.findByHiId(loginId).map(user -> user.getId()).orElse(null);
        ClickLog saved = clickLogRepository.save(new ClickLog(selectors.getId(), request.pageType(),
                referenceId, viewerUserId));
        return ViewLogResponse.from(saved);
    }

    private Long validateReference(Long selectorsId, ViewLogRequest request) {
        if (request.pageType() == com.fuma.hiselectors.analytics.model.ViewPageType.SHOP) {
            return selectorsId;
        }
        if (request.referenceId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 대상 ID가 필요합니다.");
        }
        if (request.pageType() == com.fuma.hiselectors.analytics.model.ViewPageType.GROUP) {
            groupRepository.findByIdAndSelectorsIdAndDeletedFalse(request.referenceId(), selectorsId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_GROUP_NOT_FOUND));
        } else {
            if (!productRepository.existsById(request.referenceId())
                    || !itemRepository.existsActiveProductForSelectors(selectorsId, request.referenceId())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }
        }
        return request.referenceId();
    }
}
