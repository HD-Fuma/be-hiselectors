package com.fuma.hiselectors.creator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.dto.CreatorDetailResponse;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private CreatorDiscoveryService creatorDiscoveryService;

    @BeforeEach
    void setUp() {
        creatorDiscoveryService = new CreatorDiscoveryService(
                creatorPoolRepository,
                discoveryInfoRepository,
                discoverySourceRepository);
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
        assertThat(response.email()).isEqualTo("creator@example.com");
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
}
