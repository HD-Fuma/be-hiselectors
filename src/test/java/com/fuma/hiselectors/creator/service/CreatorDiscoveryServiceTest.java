package com.fuma.hiselectors.creator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.dto.CreatorDetailResponse;
import com.fuma.hiselectors.creator.dto.CreatorPoolCategoryDemoResponse;
import com.fuma.hiselectors.creator.dto.CreatorPoolDemoResponse;
import com.fuma.hiselectors.creator.dto.CreatorPoolResetResponse;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.logging.BatchLogContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatorDiscoveryServiceTest {

    @Mock
    private CreatorPoolRepository creatorPoolRepository;
    @Mock
    private CreatorDiscoveryInfoRepository discoveryInfoRepository;
    @Mock
    private CreatorDiscoverySourceRepository discoverySourceRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BatchEventLogger batchEventLogger;

    private CreatorDiscoveryService creatorDiscoveryService;

    @BeforeEach
    void setUp() {
        creatorDiscoveryService = new CreatorDiscoveryService(
                creatorPoolRepository,
                discoveryInfoRepository,
                discoverySourceRepository,
                categoryRepository,
                batchEventLogger);
    }

    @Test
    void 크리에이터_기본정보와_발굴정보를_상세조회한다() {
        CreatorPool creator = CreatorPool.builder()
                .snsCode("YOUTUBE")
                .accountId("UC113")
                .creatorName("다예다")
                .email("creator@example.com")
                .followerCount(100_000L)
                .engagementRate(new BigDecimal("4.25"))
                .lastContentAt(LocalDateTime.of(2026, 8, 12, 20, 0))
                .category("BEAUTY")
                .build();
        CreatorDiscoveryInfo discoveryInfo = CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .brandScore(1)
                .brandHits("공식(설명)")
                .igHandle("imdayeda")
                .igConfidence(new BigDecimal("0.95"))
                .profileImageUrl("https://yt.example/profile.jpg")
                .build();
        List<CategoryShare> shares = List.of(
                new CategoryShare("BEAUTY", new BigDecimal("0.75")),
                new CategoryShare("FASHION", new BigDecimal("0.25")));

        when(creatorPoolRepository.findByIdAndDeletedFalse(113L))
                .thenReturn(Optional.of(creator));
        when(discoveryInfoRepository.findById(113L))
                .thenReturn(Optional.of(discoveryInfo));
        when(discoverySourceRepository.findCategoryShares(113L)).thenReturn(shares);

        CreatorDetailResponse response = creatorDiscoveryService.findDetail(113L);

        assertThat(response.snsCode()).isEqualTo("YOUTUBE");
        assertThat(response.accountId()).isEqualTo("UC113");
        assertThat(response.creatorName()).isEqualTo("다예다");
        assertThat(response.profileImageUrl()).isEqualTo("https://yt.example/profile.jpg");
        assertThat(response.followerCount()).isEqualTo(100_000L);
        assertThat(response.engagementRate()).isEqualByComparingTo("4.25");
        assertThat(response.category()).isEqualTo("BEAUTY");
        assertThat(response.brandScore()).isEqualTo(1);
        assertThat(response.brandHits()).isEqualTo("공식(설명)");
        assertThat(response.igHandle()).isEqualTo("imdayeda");
        assertThat(response.igConfidence()).isEqualByComparingTo("0.95");
        assertThat(response.categoryShares()).containsExactlyElementsOf(shares);
    }

    @Test
    void 발굴정보가_없는_수동등록_계정도_상세조회한다() {
        CreatorPool creator = CreatorPool.builder()
                .snsCode("INSTAGRAM")
                .accountId("17841400000000000")
                .creatorName("수동 등록 계정")
                .build();

        when(creatorPoolRepository.findByIdAndDeletedFalse(121L))
                .thenReturn(Optional.of(creator));
        when(discoveryInfoRepository.findById(121L)).thenReturn(Optional.empty());
        when(discoverySourceRepository.findCategoryShares(121L)).thenReturn(List.of());

        CreatorDetailResponse response = creatorDiscoveryService.findDetail(121L);

        assertThat(response.brandScore()).isNull();
        assertThat(response.igHandle()).isNull();
        assertThat(response.firstDiscoveredAt()).isNull();
        assertThat(response.categoryShares()).isEmpty();
    }

    @Test
    void 존재하지_않거나_삭제된_크리에이터는_조회할_수_없다() {
        when(creatorPoolRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorDiscoveryService.findDetail(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CREATOR_NOT_FOUND);

        verify(discoveryInfoRepository, never()).findById(999L);
        verify(discoverySourceRepository, never()).findCategoryShares(999L);
    }

    @Test
    void 기존_유튜브와_인스타그램_풀을_안전하게_초기화한다() {
        BatchLogContext logContext = mock(BatchLogContext.class);
        when(batchEventLogger.start("creator-pool-reset")).thenReturn(logContext);
        when(discoverySourceRepository.deleteAllByCreatorPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"))).thenReturn(515);
        when(discoveryInfoRepository.deleteAllByCreatorPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"))).thenReturn(497);
        when(creatorPoolRepository.softDeleteAllActiveByPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"))).thenReturn(598);

        CreatorPoolResetResponse response = creatorDiscoveryService.resetPool(
                "DELETE_CREATOR_POOL", "admin");

        assertThat(response.softDeletedCount()).isEqualTo(598);
        InOrder order = inOrder(
                discoverySourceRepository, discoveryInfoRepository, creatorPoolRepository);
        order.verify(discoverySourceRepository).deleteAllByCreatorPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"));
        order.verify(discoveryInfoRepository).deleteAllByCreatorPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"));
        order.verify(creatorPoolRepository).softDeleteAllActiveByPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"));
        verify(batchEventLogger).succeeded(logContext, Map.of(
                "deletedSourceCount", 515L,
                "deletedInfoCount", 497L,
                "softDeletedCount", 598L), Map.of("adminLoginId", "admin"));
    }

    @Test
    void 초기화_확인_문구가_다르면_아무것도_지우지_않는다() {
        assertThatThrownBy(() -> creatorDiscoveryService.resetPool("초기화", "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(creatorPoolRepository, discoveryInfoRepository,
                discoverySourceRepository, batchEventLogger);
    }

    @Test
    void 데모_풀은_일반_카테고리_10명과_리빙라이프_2명만_복원한다() {
        BatchLogContext logContext = mock(BatchLogContext.class);
        when(batchEventLogger.start("creator-pool-demo")).thenReturn(logContext);
        List<CreatorPool> creators = java.util.stream.Stream.concat(
                java.util.stream.IntStream.range(0, 12)
                        .mapToObj(index -> deletedCreator("BEAUTY", "beauty-" + index)),
                java.util.stream.IntStream.range(0, 4)
                        .mapToObj(index -> deletedCreator("LIVING_LIFE", "living-" + index)))
                .toList();
        when(creatorPoolRepository.findDeletedDemoCandidatesWithProfileImage(
                List.of("YOUTUBE", "INSTAGRAM"))).thenReturn(creators);

        CreatorPoolDemoResponse response = creatorDiscoveryService.prepareDemo("admin");

        assertThat(response.restoredCount()).isEqualTo(12);
        assertThat(creators.stream().filter(creator -> !creator.isDeleted())
                .filter(creator -> "BEAUTY".equals(creator.getCategory()))).hasSize(10);
        assertThat(creators.stream().filter(creator -> !creator.isDeleted())
                .filter(creator -> "LIVING_LIFE".equals(creator.getCategory()))).hasSize(2);
        verify(creatorPoolRepository).softDeleteAllActiveByPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"));
        verify(batchEventLogger).succeeded(logContext, Map.of("restoredCount", 12L),
                Map.of("adminLoginId", "admin"));
    }

    @Test
    void FAST_모드_카테고리_데모_발굴은_해당_카테고리_전체를_노출한다() {
        BatchLogContext logContext = mock(BatchLogContext.class);
        when(batchEventLogger.start("creator-pool-demo-category")).thenReturn(logContext);
        Category category = mock(Category.class);
        when(category.getCode()).thenReturn("LIVING_LIFE");
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
        CreatorPool alreadyVisible = CreatorPool.builder()
                .snsCode("YOUTUBE").accountId("living-3").category("LIVING_LIFE").build();
        List<CreatorPool> creators = List.of(
                deletedCreator("LIVING_LIFE", "living-1"),
                deletedCreator("LIVING_LIFE", "living-2"),
                alreadyVisible);
        when(creatorPoolRepository.findDemoCandidatesByCategory(
                "LIVING_LIFE", List.of("YOUTUBE", "INSTAGRAM"))).thenReturn(creators);

        CreatorPoolCategoryDemoResponse response =
                creatorDiscoveryService.prepareCategoryDemo(4L, "admin");

        assertThat(response.restoredCount()).isEqualTo(3);
        assertThat(response.restoredCreatorIds()).hasSize(3);
        assertThat(creators).allMatch(creator -> !creator.isDeleted());
        verify(creatorPoolRepository, never()).softDeleteAllActiveByPlatforms(anyList());
        verify(batchEventLogger).succeeded(logContext, Map.of("restoredCount", 3L),
                Map.of("adminLoginId", "admin", "categoryCode", "LIVING_LIFE"));
    }

    @Test
    void FAST_모드_카테고리_데모_발굴은_없는_카테고리면_실패한다() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorDiscoveryService.prepareCategoryDemo(99L, "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
        verifyNoInteractions(batchEventLogger);
    }

    private CreatorPool deletedCreator(String category, String accountId) {
        CreatorPool creator = CreatorPool.builder()
                .snsCode("YOUTUBE")
                .accountId(accountId)
                .category(category)
                .build();
        creator.softDelete();
        return creator;
    }
}
