package com.fuma.hiselectors.productgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupItemRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductGroupServiceTest {

    @Test
    void publicProductDoesNotRequireProductGroupMembership() {
        ProductGroupItemRepository itemRepository = mock(ProductGroupItemRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        ProductGroupService service = new ProductGroupService(
                mock(ProductGroupRepository.class), itemRepository,
                mock(CampaignRepository.class), mock(CampaignProductRepository.class),
                mock(UserRepository.class), selectorsRepository,
                mock(SelectorsGenerationRepository.class), mock(SelectorsSnsAccountRepository.class),
                productRepository, selectorAccessService);
        Selectors selectors = mock(Selectors.class);
        Product product = mock(Product.class);
        when(selectorsRepository.findBySelectorsCode("SEL-1")).thenReturn(Optional.of(selectors));
        when(selectorAccessService.requireReadable(selectors)).thenReturn(selectors);
        when(productRepository.findByProductCode("P-1")).thenReturn(Optional.of(product));
        when(product.getProductCode()).thenReturn("P-1");

        assertThat(service.findPublicProductByCode("SEL-1", "P-1").code()).isEqualTo("P-1");
        verify(itemRepository, never()).existsActiveProductForSelectors(selectors.getId(), product.getId());
    }

    @Test
    void publicShopDoesNotExposeBlacklistedSelector() {
        ProductGroupRepository groupRepository = mock(ProductGroupRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        ProductGroupService service = new ProductGroupService(
                groupRepository,
                mock(ProductGroupItemRepository.class),
                mock(CampaignRepository.class),
                mock(CampaignProductRepository.class),
                mock(UserRepository.class),
                selectorsRepository,
                mock(SelectorsGenerationRepository.class),
                mock(SelectorsSnsAccountRepository.class),
                mock(ProductRepository.class),
                mock(SelectorAccessService.class));
        Selectors selectors = mock(Selectors.class);
        when(selectors.isBlacklisted()).thenReturn(true);
        when(selectorsRepository.findBySelectorsCode("SEL-1"))
                .thenReturn(Optional.of(selectors));

        assertThatThrownBy(() -> service.findPublic("SEL-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SELECTOR_NOT_FOUND);

        verify(groupRepository, never())
                .findAllBySelectorsIdAndDeletedFalseOrderByGroupNoAscIdAsc(
                        org.mockito.ArgumentMatchers.any());
    }
}
