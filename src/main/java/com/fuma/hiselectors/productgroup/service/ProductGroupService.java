package com.fuma.hiselectors.productgroup.service;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.campaign.dto.CampaignProductDisplayResponse;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.productgroup.dto.MySelectorsShopResponse;
import com.fuma.hiselectors.productgroup.dto.ProductGroupItemAddRequest;
import com.fuma.hiselectors.productgroup.dto.ProductGroupResponse;
import com.fuma.hiselectors.productgroup.dto.ProductGroupSaveRequest;
import com.fuma.hiselectors.productgroup.dto.SelectorsShopResponse;
import com.fuma.hiselectors.productgroup.model.ProductGroup;
import com.fuma.hiselectors.productgroup.model.ProductGroupItem;
import com.fuma.hiselectors.productgroup.repository.ProductGroupItemRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductGroupService {

    private final ProductGroupRepository groupRepository;
    private final ProductGroupItemRepository itemRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignProductRepository campaignProductRepository;
    private final UserRepository userRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    private final ProductRepository productRepository;
    private final SelectorAccessService selectorAccessService;

    public List<ProductGroupResponse> findMine(String loginId) {
        return findBySelectors(selectorAccessService.requireReadable(loginId).getId());
    }

    public MySelectorsShopResponse findMineShop(String loginId) {
        User user = findUser(loginId);
        Selectors selectors = selectorAccessService.requireReadable(loginId);
        Optional<SelectorsSnsAccount> representativeAccount = findRepresentativeAccount(selectors.getId());
        String generationName = selectorsGenerationRepository.findGenerationsOf(selectors.getId()).stream()
                .findFirst()
                .map(generation -> generation.generationName())
                .orElse(null);
        return new MySelectorsShopResponse(selectors.getSelectorsCode(), selectors.getSelectorsNickname(),
                representativeAccount.map(SelectorsSnsAccount::getProfileImageUrl).orElse(null),
                generationName, user.getName(),
                representativeAccount.map(SelectorsSnsAccount::getAccountId).orElse(null),
                findBySelectors(selectors.getId()));
    }

    public List<ProductGroupResponse> findPublic(String selectorsCode) {
        Selectors selectors = findPublicSelectors(selectorsCode);
        return findBySelectors(selectors.getId());
    }

    public SelectorsShopResponse findPublicShop(String selectorsCode) {
        return toShopResponse(findPublicSelectors(selectorsCode));
    }

    public CampaignProductDisplayResponse findPublicProduct(String selectorsCode, Long productId) {
        Selectors selectors = findPublicSelectors(selectorsCode);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return findPublicProduct(selectors, product);
    }

    public CampaignProductDisplayResponse findPublicProductByCode(
            String selectorsCode, String productCode) {
        Selectors selectors = findPublicSelectors(selectorsCode);
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return findPublicProduct(selectors, product);
    }

    private CampaignProductDisplayResponse findPublicProduct(Selectors selectors, Product product) {
        if (!itemRepository.existsActiveProductForSelectors(selectors.getId(), product.getId())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return CampaignProductDisplayResponse.of(product);
    }

    private SelectorsShopResponse toShopResponse(Selectors selectors) {
        String profileImageUrl = findRepresentativeAccount(selectors.getId())
                .map(SelectorsSnsAccount::getProfileImageUrl).orElse(null);
        return new SelectorsShopResponse(selectors.getSelectorsCode(), selectors.getSelectorsNickname(),
                profileImageUrl, findBySelectors(selectors.getId()));
    }

    @Transactional
    public ProductGroupResponse create(String loginId, ProductGroupSaveRequest request) {
        Selectors selectors = lockSelectors(selectorAccessService.requireCurrent(loginId).getId());
        Campaign campaign = findCampaign(request.campaignId());
        List<Product> products = validateProducts(campaign.getId(), request.productIds());
        int nextGroupNoValue = groupRepository.findFirstBySelectorsIdOrderByGroupNoDesc(selectors.getId())
                .map(group -> group.getGroupNo().intValue() + 1).orElse(1);
        short nextGroupNo = (short) nextGroupNoValue;
        ProductGroup group = groupRepository.save(new ProductGroup(selectors.getId(), campaign.getId(),
                nextGroupNo, request.title().trim()));
        List<ProductGroupItem> items = saveItems(group, products, 1);
        return ProductGroupResponse.of(group, items);
    }

    @Transactional
    public ProductGroupResponse update(String loginId, Long groupId, ProductGroupSaveRequest request) {
        Selectors selectors = selectorAccessService.requireCurrent(loginId);
        ProductGroup group = findOwnedGroup(groupId, selectors.getId());
        Campaign campaign = findCampaign(request.campaignId());
        List<Product> products = validateProducts(campaign.getId(), request.productIds());
        group.update(campaign.getId(), request.title().trim());
        return ProductGroupResponse.of(group, replaceItems(group, products));
    }

    @Transactional
    public ProductGroupResponse addItems(String loginId, Long groupId, ProductGroupItemAddRequest request) {
        Selectors selectors = selectorAccessService.requireCurrent(loginId);
        ProductGroup group = findOwnedGroup(groupId, selectors.getId());
        List<ProductGroupItem> existingItems = itemRepository
                .findAllByGroupIdAndDeletedFalseOrderByDisplayOrderAsc(groupId);
        Set<Long> existingProductIds = existingItems.stream().map(item -> item.getProduct().getId())
                .collect(Collectors.toSet());
        List<Long> newProductIds = request.productIds().stream().distinct()
                .filter(productId -> !existingProductIds.contains(productId)).toList();
        if (existingItems.size() + newProductIds.size() > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한 상품 그룹에는 최대 100개 상품을 담을 수 있습니다.");
        }
        if (!newProductIds.isEmpty()) {
            List<Product> products = validateProducts(group.getCampaignId(), newProductIds);
            existingItems = new ArrayList<>(existingItems);
            int nextDisplayOrder = existingItems.stream()
                    .mapToInt(item -> item.getDisplayOrder().intValue()).max().orElse(0) + 1;
            existingItems.addAll(restoreOrSaveItems(group, products, nextDisplayOrder));
        }
        return ProductGroupResponse.of(group, existingItems);
    }

    @Transactional
    public void delete(String loginId, Long groupId) {
        Selectors selectors = selectorAccessService.requireCurrent(loginId);
        ProductGroup group = findOwnedGroup(groupId, selectors.getId());
        itemRepository.findAllByGroupIdAndDeletedFalseOrderByDisplayOrderAsc(groupId)
                .forEach(ProductGroupItem::softDelete);
        group.softDelete();
    }

    private List<ProductGroupResponse> findBySelectors(Long selectorsId) {
        List<ProductGroup> groups = groupRepository
                .findAllBySelectorsIdAndDeletedFalseOrderByGroupNoAscIdAsc(selectorsId);
        if (groups.isEmpty()) return List.of();
        Map<Long, List<ProductGroupItem>> itemsByGroup = itemRepository
                .findAllByGroupIdInAndDeletedFalseOrderByGroupIdAscDisplayOrderAsc(
                        groups.stream().map(ProductGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getGroup().getId()));
        return groups.stream().map(group -> ProductGroupResponse.of(group,
                itemsByGroup.getOrDefault(group.getId(), List.of()))).toList();
    }

    private List<Product> validateProducts(Long campaignId, List<Long> requestedProductIds) {
        List<Long> productIds = requestedProductIds.stream().distinct().toList();
        Map<Long, Product> campaignProducts = campaignProductRepository.findAllByCampaignIdOrderByIdAsc(campaignId)
                .stream().map(CampaignProduct::getProduct)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        if (!campaignProducts.keySet().containsAll(productIds)) {
            throw new BusinessException(ErrorCode.PRODUCT_GROUP_CAMPAIGN_MISMATCH);
        }
        return productIds.stream().map(campaignProducts::get).toList();
    }

    private List<ProductGroupItem> saveItems(ProductGroup group, List<Product> products, int startOrder) {
        List<ProductGroupItem> items = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            items.add(new ProductGroupItem(group, products.get(index), (short) (startOrder + index)));
        }
        return itemRepository.saveAll(items);
    }

    private List<ProductGroupItem> replaceItems(ProductGroup group, List<Product> products) {
        List<ProductGroupItem> allItems = itemRepository.findAllByGroupIdOrderByDisplayOrderAsc(group.getId());
        Map<Long, ProductGroupItem> itemByProductId = allItems.stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));
        allItems.forEach(ProductGroupItem::softDelete);

        List<ProductGroupItem> result = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            Product product = products.get(index);
            ProductGroupItem item = itemByProductId.get(product.getId());
            short displayOrder = (short) (index + 1);
            if (item == null) {
                item = new ProductGroupItem(group, product, displayOrder);
            } else {
                item.restore(displayOrder);
            }
            result.add(item);
        }
        return itemRepository.saveAll(result);
    }

    private List<ProductGroupItem> restoreOrSaveItems(ProductGroup group, List<Product> products,
            int startOrder) {
        Map<Long, ProductGroupItem> itemByProductId = itemRepository
                .findAllByGroupIdOrderByDisplayOrderAsc(group.getId()).stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), Function.identity()));
        List<ProductGroupItem> result = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            Product product = products.get(index);
            short displayOrder = (short) (startOrder + index);
            ProductGroupItem item = itemByProductId.get(product.getId());
            if (item == null) {
                item = new ProductGroupItem(group, product, displayOrder);
            } else {
                item.restore(displayOrder);
            }
            result.add(item);
        }
        return itemRepository.saveAll(result);
    }

    private Campaign findCampaign(Long campaignId) {
        return campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
    }

    private User findUser(String loginId) {
        return userRepository.findByHiId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private Optional<SelectorsSnsAccount> findRepresentativeAccount(Long selectorsId) {
        return selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalse(selectorsId);
    }

    private Selectors findPublicSelectors(String selectorsCode) {
        Selectors selectors = selectorsRepository.findBySelectorsCode(selectorsCode)
                .filter(value -> !value.isDeleted() && !value.isBlacklisted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        return selectorAccessService.requireReadable(selectors);
    }

    private Selectors lockSelectors(Long selectorsId) {
        return selectorsRepository.findByIdForUpdate(selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
    }

    private ProductGroup findOwnedGroup(Long groupId, Long selectorsId) {
        return groupRepository.findByIdAndSelectorsIdAndDeletedFalse(groupId, selectorsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_GROUP_NOT_FOUND));
    }
}
