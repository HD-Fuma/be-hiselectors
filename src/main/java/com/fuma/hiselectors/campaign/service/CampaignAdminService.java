package com.fuma.hiselectors.campaign.service;

import com.fuma.hiselectors.campaign.dto.CampaignCreateRequest;
import com.fuma.hiselectors.campaign.dto.CampaignParticipantResponse;
import com.fuma.hiselectors.campaign.dto.CampaignResponse;
import com.fuma.hiselectors.campaign.dto.CampaignUpdateRequest;
import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignAdminService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final CampaignRepository campaignRepository;
    private final CampaignProductRepository campaignProductRepository;
    private final ProductRepository productRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public Page<CampaignResponse> search(String keyword, LocalDate startDate, LocalDate endDate,
                                         CampaignStatus status, Pageable pageable) {
        Page<Campaign> campaigns = campaignRepository.findAll(
                searchSpecification(keyword, startDate, endDate, status), pageable);
        Map<Long, List<CampaignProduct>> productsByCampaignId = campaigns.isEmpty()
                ? Map.of()
                : campaignProductRepository.findAllByCampaignIdInOrderByCampaignIdAscIdAsc(
                                campaigns.getContent().stream().map(Campaign::getId).toList())
                        .stream().collect(Collectors.groupingBy(link -> link.getCampaign().getId()));
        return campaigns.map(campaign -> toResponse(campaign,
                productsByCampaignId.getOrDefault(campaign.getId(), List.of())));
    }

    public CampaignResponse findOne(Long campaignId) {
        return toResponse(getCampaign(campaignId));
    }

    public Page<CampaignParticipantResponse> findParticipants(Long campaignId, Pageable pageable) {
        Campaign campaign = getCampaign(campaignId);
        LocalDateTime startAt = campaign.getStartDate().atStartOfDay();
        LocalDateTime endExclusive = campaign.getEndDate().plusDays(1).atStartOfDay();
        return campaignRepository.findParticipants(campaignId, startAt, endExclusive, pageable)
                .map(CampaignParticipantResponse::from);
    }

    @Transactional
    public CampaignResponse create(CampaignCreateRequest request) {
        validateDates(request.startDate(), request.endDate());
        List<Product> products = getProducts(request.productIds());
        ensureAllAvailable(products);
        Campaign campaign = campaignRepository.save(Campaign.builder()
                .title(request.title().trim()).description(request.description().trim())
                .startDate(request.startDate()).endDate(request.endDate())
                .thumbnailUrl(trimToNull(request.thumbnailUrl())).build());
        saveLinks(campaign, products);
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse update(Long campaignId, CampaignUpdateRequest request) {
        Campaign campaign = getCampaignForUpdate(campaignId);
        LocalDate startDate = request.startDate() == null ? campaign.getStartDate() : request.startDate();
        LocalDate endDate = request.endDate() == null ? campaign.getEndDate() : request.endDate();
        validateDates(startDate, endDate);
        String previousThumbnailUrl = campaign.getThumbnailUrl();
        String requestedThumbnailUrl = trimToNull(request.thumbnailUrl());
        campaign.update(trimToNull(request.title()), trimToNull(request.description()), request.startDate(),
                request.endDate(), requestedThumbnailUrl);
        boolean removeThumbnail = Boolean.TRUE.equals(request.removeThumbnail());
        if (removeThumbnail) {
            campaign.clearThumbnail();
        }
        publishThumbnailRemovals(previousThumbnailUrl, requestedThumbnailUrl,
                campaign.getThumbnailUrl(), removeThumbnail);

        if (request.productIds() != null) {
            List<CampaignProduct> existingLinks = campaignProductRepository.findAllByCampaignIdOrderByIdAsc(campaignId);
            Set<Long> existingProductIds = existingLinks.stream()
                    .map(link -> link.getProduct().getId()).collect(java.util.stream.Collectors.toSet());
            List<Product> requestedProducts = getProducts(request.productIds());
            ensureAllAvailable(requestedProducts.stream()
                    .filter(product -> !existingProductIds.contains(product.getId())).toList());
            campaignProductRepository.deleteAllInBatch(existingLinks);
            saveLinks(campaign, requestedProducts);
        }
        return toResponse(campaign);
    }

    @Transactional
    public void delete(Long campaignId) {
        Campaign campaign = getCampaignForUpdate(campaignId);
        if (!campaign.getEndDate().isBefore(today())) {
            throw new BusinessException(ErrorCode.CAMPAIGN_DELETE_NOT_ALLOWED);
        }
        campaign.softDelete();
    }

    private Specification<Campaign> searchSpecification(String keyword, LocalDate startDate, LocalDate endDate,
                                                         CampaignStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("isDeleted")));
            if (keyword != null && !keyword.isBlank()) {
                String value = keyword.trim();
                Predicate title = builder.like(builder.lower(root.get("title")), "%" + value.toLowerCase() + "%");
                try {
                    predicates.add(builder.or(title, builder.equal(root.get("id"), Long.valueOf(value))));
                } catch (NumberFormatException ignored) {
                    predicates.add(title);
                }
            }
            if (startDate != null) predicates.add(builder.greaterThanOrEqualTo(root.get("endDate"), startDate));
            if (endDate != null) predicates.add(builder.lessThanOrEqualTo(root.get("startDate"), endDate));
            if (status != null) {
                LocalDate today = today();
                switch (status) {
                    case SCHEDULED -> predicates.add(builder.greaterThan(root.get("startDate"), today));
                    case ACTIVE -> {
                        predicates.add(builder.lessThanOrEqualTo(root.get("startDate"), today));
                        predicates.add(builder.greaterThanOrEqualTo(root.get("endDate"), today));
                    }
                    case ENDED -> predicates.add(builder.lessThan(root.get("endDate"), today));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Campaign getCampaign(Long campaignId) {
        return campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
    }

    private Campaign getCampaignForUpdate(Long campaignId) {
        return campaignRepository.findByIdAndIsDeletedFalseForUpdate(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
    }

    private void publishThumbnailRemovals(String previousUrl, String requestedUrl,
                                          String currentUrl, boolean removeThumbnail) {
        Set<String> removalUrls = new LinkedHashSet<>();
        if (previousUrl != null && !Objects.equals(previousUrl, currentUrl)) {
            removalUrls.add(previousUrl);
        }
        if (removeThumbnail && requestedUrl != null && !Objects.equals(requestedUrl, currentUrl)) {
            removalUrls.add(requestedUrl);
        }
        removalUrls.forEach(url -> eventPublisher.publishEvent(new CampaignThumbnailRemovalRequested(url)));
    }

    private List<Product> getProducts(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        if (productIds.stream().anyMatch(id -> id == null) || new HashSet<>(productIds).size() != productIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "상품 ID는 중복 없이 입력해야 합니다.");
        }
        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.size()) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        return products;
    }

    private void ensureAllAvailable(Collection<Product> products) {
        if (products.stream().anyMatch(product -> !product.isAvailableForSale())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        }
    }

    private void saveLinks(Campaign campaign, List<Product> products) {
        campaignProductRepository.saveAll(products.stream()
                .map(product -> new CampaignProduct(campaign, product)).toList());
    }

    private CampaignResponse toResponse(Campaign campaign) {
        return toResponse(campaign, campaignProductRepository.findAllByCampaignIdOrderByIdAsc(campaign.getId()));
    }

    private CampaignResponse toResponse(Campaign campaign, List<CampaignProduct> products) {
        return CampaignResponse.of(campaign, products,
                CampaignStatus.from(campaign.getStartDate(), campaign.getEndDate(), today()));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(SEOUL_ZONE));
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }
}
