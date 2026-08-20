package com.fuma.hiselectors.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.analytics.dto.ViewLogRequest;
import com.fuma.hiselectors.analytics.model.ClickLog;
import com.fuma.hiselectors.analytics.model.ViewPageType;
import com.fuma.hiselectors.analytics.repository.ClickLogRepository;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupItemRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ViewLogServiceTest {

    private ClickLogRepository clickLogRepository;
    private SelectorsRepository selectorsRepository;
    private ProductGroupRepository groupRepository;
    private ProductGroupItemRepository itemRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private ViewLogService service;

    @BeforeEach
    void setUp() {
        clickLogRepository = mock(ClickLogRepository.class);
        selectorsRepository = mock(SelectorsRepository.class);
        groupRepository = mock(ProductGroupRepository.class);
        itemRepository = mock(ProductGroupItemRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ViewLogService(clickLogRepository, selectorsRepository, groupRepository,
                itemRepository, productRepository, userRepository);

        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(7L);
        when(selectorsRepository.findBySelectorsCode("SEL-1")).thenReturn(Optional.of(selectors));
        when(clickLogRepository.save(any())).thenAnswer(invocation -> {
            ClickLog log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 11L);
            return log;
        });
    }

    @Test
    void recordsAnonymousShopViewWithSelectorsAsReference() {
        var response = service.record(null,
                new ViewLogRequest("SEL-1", ViewPageType.SHOP, null));

        assertThat(response.selectorsId()).isEqualTo(7L);
        assertThat(response.referenceId()).isEqualTo(7L);
        assertThat(response.viewerUserId()).isNull();
        verify(clickLogRepository).save(any(ClickLog.class));
    }

    @Test
    void recordsMemberIdentityForProductView() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(3L);
        when(userRepository.findByHiId("buyer")).thenReturn(Optional.of(user));
        when(productRepository.existsById(9L)).thenReturn(true);
        when(itemRepository.existsActiveProductForSelectors(7L, 9L)).thenReturn(true);

        var response = service.record("buyer",
                new ViewLogRequest("SEL-1", ViewPageType.PRODUCT, 9L));

        assertThat(response.viewerUserId()).isEqualTo(3L);
    }
}
