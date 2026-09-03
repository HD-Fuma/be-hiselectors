package com.fuma.hiselectors.creator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorDiscoverySource;
import com.fuma.hiselectors.creator.model.CreatorPool;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 커스텀 JPQL 은 애플리케이션 기동 시점에 파싱된다. 문법이 틀리면 앱 자체가 뜨지 않으므로
 * 매핑과 쿼리를 여기서 먼저 검증한다.
 */
@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CreatorDiscoveryRepositoryTest.CacheConfig.class)
class CreatorDiscoveryRepositoryTest {

    @Autowired
    private CreatorPoolRepository creatorPoolRepository;

    @Autowired
    private CreatorDiscoveryInfoRepository infoRepository;

    @Autowired
    private CreatorDiscoverySourceRepository sourceRepository;

    @Autowired
    private TestEntityManager em;

    private CreatorPool saveCreator(String snsCode, String accountId, String name) {
        return creatorPoolRepository.save(CreatorPool.builder()
                .snsCode(snsCode)
                .accountId(accountId)
                .creatorName(name)
                .followerCount(120_000L)
                .engagementRate(new BigDecimal("4.53"))
                .lastContentAt(LocalDateTime.now().minusDays(3))
                .build());
    }

    private CreatorPool saveCategorizedCreator(String accountId, String category) {
        return creatorPoolRepository.save(CreatorPool.builder()
                .snsCode("YOUTUBE")
                .accountId(accountId)
                .creatorName(accountId)
                .category(category)
                .build());
    }

    private DiscoveryKeyword saveKeyword(String categoryCode, String categoryName, String keyword) {
        Category category = em.persist(Category.builder()
                .code(categoryCode).name(categoryName).build());
        DiscoveryKeyword created = category.addKeyword(keyword, 0);
        em.flush();
        return created;
    }

    @Test
    @DisplayName("SNS 코드 + 계정 ID 로 중복 여부를 확인한다")
    void findBySnsCodeAndAccountId() {
        saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        em.flush();
        em.clear();

        assertThat(creatorPoolRepository.findFirstBySnsCodeAndAccountIdOrderByIdAsc("YOUTUBE", "UC_fit01"))
                .isPresent();
        assertThat(creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc("INSTAGRAM", "UC_fit01"))
                .isEmpty();
    }

    @Test
    @DisplayName("동일인의 유튜브·인스타 계정은 별개 행으로 각각 등록된다")
    void sameCreatorStoredAsSeparateAccounts() {
        saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        saveCreator("INSTAGRAM", "fitgpt_daily", "핏지피티 홈트");
        em.flush();
        em.clear();

        assertThat(creatorPoolRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("발굴 정보는 creator_pool 과 PK 를 공유한다 (@MapsId)")
    void discoveryInfoSharesPrimaryKey() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .brandScore(0)
                .igHandle("fitgpt_daily")
                .igConfidence(new BigDecimal("0.95"))
                .build());
        em.flush();
        em.clear();

        CreatorDiscoveryInfo found = infoRepository.findById(creator.getId()).orElseThrow();
        assertThat(found.getId()).isEqualTo(creator.getId());
        assertThat(found.getIgHandle()).isEqualTo("fitgpt_daily");
    }

    @Test
    @DisplayName("빈 값이나 500자 초과 URL은 기존 프로필 이미지를 지우지 않는다")
    void invalidProfileImageDoesNotReplaceExistingValue() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        CreatorDiscoveryInfo info = infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .profileImageUrl("https://yt.example/original.jpg")
                .build());

        info.updateProfileImageUrl(null);
        info.updateProfileImageUrl("   ");
        info.updateProfileImageUrl("x".repeat(501));
        em.flush();
        em.clear();

        assertThat(infoRepository.findById(creator.getId()).orElseThrow().getProfileImageUrl())
                .isEqualTo("https://yt.example/original.jpg");
    }

    @Test
    @DisplayName("신뢰도가 더 낮은 핸들로는 덮어쓰지 않는다")
    void refreshKeepsHigherConfidenceHandle() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        CreatorDiscoveryInfo info = infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .igHandle("fitgpt_daily")
                .igConfidence(new BigDecimal("0.95"))   // URL 로 찾은 것
                .build());
        em.flush();

        // 단순 멘션(0.35)으로 다시 발굴돼도 URL 로 찾은 핸들을 유지해야 한다
        info.refresh(0, null, "some_other", new BigDecimal("0.35"));
        em.flush();
        em.clear();

        CreatorDiscoveryInfo found = infoRepository.findById(creator.getId()).orElseThrow();
        assertThat(found.getIgHandle()).isEqualTo("fitgpt_daily");
        assertThat(found.getIgConfidence()).isEqualByComparingTo("0.95");
    }

    @Test
    @DisplayName("신뢰도가 같으면 최신 핸들로 교체한다 — 크리에이터가 계정을 바꾼 경우")
    void refreshReplacesHandleOnEqualConfidence() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        CreatorDiscoveryInfo info = infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .igHandle("old_handle")
                .igConfidence(new BigDecimal("0.95"))
                .build());
        em.flush();

        // 채널 설명의 인스타 계정이 바뀌었고 이번에도 URL(0.95) 로 찾았다
        info.refresh(0, null, "new_handle", new BigDecimal("0.95"));
        em.flush();
        em.clear();

        CreatorDiscoveryInfo found = infoRepository.findById(creator.getId()).orElseThrow();
        assertThat(found.getIgHandle()).isEqualTo("new_handle");
    }

    @Test
    @DisplayName("기존 신뢰도가 없으면 새 핸들로 교체한다")
    void refreshReplacesWhenExistingConfidenceIsNull() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        CreatorDiscoveryInfo info = infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .igHandle("unknown_source")   // 신뢰도 미상
                .build());
        em.flush();

        info.refresh(0, null, "from_url", new BigDecimal("0.95"));
        em.flush();
        em.clear();

        CreatorDiscoveryInfo found = infoRepository.findById(creator.getId()).orElseThrow();
        assertThat(found.getIgHandle()).isEqualTo("from_url");
        assertThat(found.getIgConfidence()).isEqualByComparingTo("0.95");
    }

    @Test
    @DisplayName("새 신뢰도를 모르면 기존 핸들을 유지한다")
    void refreshKeepsHandleWhenNewConfidenceIsNull() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        CreatorDiscoveryInfo info = infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .igHandle("fitgpt_daily")
                .igConfidence(new BigDecimal("0.95"))
                .build());
        em.flush();

        info.refresh(0, null, "no_confidence", null);
        em.flush();
        em.clear();

        CreatorDiscoveryInfo found = infoRepository.findById(creator.getId()).orElseThrow();
        assertThat(found.getIgHandle()).isEqualTo("fitgpt_daily");
    }

    @Test
    @DisplayName("이번 수집에서 핸들을 못 찾으면 기존 값을 유지한다")
    void refreshKeepsHandleWhenNotFound() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        CreatorDiscoveryInfo info = infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .igHandle("fitgpt_daily")
                .igConfidence(new BigDecimal("0.95"))
                .build());
        em.flush();

        info.refresh(3, "공식(설명)", null, null);
        em.flush();
        em.clear();

        CreatorDiscoveryInfo found = infoRepository.findById(creator.getId()).orElseThrow();
        assertThat(found.getIgHandle()).isEqualTo("fitgpt_daily");
        // 브랜드 신호는 매번 최신 값으로 갱신된다
        assertThat(found.getBrandScore()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 계정·키워드 조합의 발굴 출처를 찾아낸다")
    void findSourceByCreatorAndKeyword() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        DiscoveryKeyword keyword = saveKeyword("FITNESS", "피트니스", "홈트레이닝");
        sourceRepository.save(CreatorDiscoverySource.builder()
                .creatorPool(creator).keyword(keyword)
                .viewShare(new BigDecimal("1.00000")).build());
        em.flush();
        em.clear();

        assertThat(sourceRepository.findByCreatorPoolIdAndKeywordId(
                creator.getId(), keyword.getId())).isPresent();
        assertThat(sourceRepository.findByCreatorPoolId(creator.getId())).hasSize(1);
    }

    @Test
    @DisplayName("대표 카테고리는 조회수 비중 합이 가장 큰 쪽이다")
    void categorySharesOrderedByViewShare() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_beauty01", "코스민");

        // 뷰티 2개(0.45 + 0.30 = 0.75) vs 패션 1개(0.25)
        // 홈트 영상 하나로 다른 카테고리에 걸려도 무게중심은 뷰티여야 한다
        DiscoveryKeyword grwm = saveKeyword("BEAUTY", "뷰티", "grwm");
        DiscoveryKeyword daily = grwm.getCategory().addKeyword("데일리 메이크업", 0);
        DiscoveryKeyword haul = saveKeyword("FASHION", "패션", "하울");
        em.flush();

        sourceRepository.save(CreatorDiscoverySource.builder()
                .creatorPool(creator).keyword(grwm).viewShare(new BigDecimal("0.45000")).build());
        sourceRepository.save(CreatorDiscoverySource.builder()
                .creatorPool(creator).keyword(daily).viewShare(new BigDecimal("0.30000")).build());
        sourceRepository.save(CreatorDiscoverySource.builder()
                .creatorPool(creator).keyword(haul).viewShare(new BigDecimal("0.25000")).build());
        em.flush();
        em.clear();

        List<CategoryShare> shares = sourceRepository.findCategoryShares(creator.getId());

        assertThat(shares).hasSize(2);
        assertThat(shares.getFirst().categoryCode()).isEqualTo("BEAUTY");
        assertThat(shares.getFirst().totalShare()).isEqualByComparingTo("0.75");
        assertThat(shares.get(1).categoryCode()).isEqualTo("FASHION");
    }

    @Test
    @DisplayName("소프트 삭제된 계정도 중복 확인 대상에 포함된다")
    void deletedCreatorIsStillFoundForDedup() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        creator.softDelete();
        em.flush();
        em.clear();

        // 발굴 파이프라인용 — 지워진 계정도 찾아야 중복 행이 안 생긴다
        assertThat(creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc("YOUTUBE", "UC_fit01"))
                .isPresent();

        // 화면 조회용 — 지워진 계정은 빠진다
        assertThat(creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdAndDeletedFalseOrderByIdAsc("YOUTUBE", "UC_fit01"))
                .isEmpty();
    }

    @Test
    @DisplayName("소프트 삭제된 계정을 되살릴 수 있다")
    void restoreDeletedCreator() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        creator.softDelete();
        em.flush();

        creator.restore();
        em.flush();
        em.clear();

        assertThat(creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdAndDeletedFalseOrderByIdAsc("YOUTUBE", "UC_fit01"))
                .isPresent();
    }

    @Test
    @DisplayName("데모 후보는 프로필 사진이 있는 소프트 삭제 계정만 조회한다")
    void findDeletedDemoCandidatesWithProfileImage() {
        CreatorPool withImage = saveCreator("YOUTUBE", "UC_photo", "사진 있음");
        CreatorPool withoutImage = saveCreator("YOUTUBE", "UC_no_photo", "사진 없음");
        for (CreatorPool creator : List.of(withImage, withoutImage)) {
            creator.softDelete();
        }
        infoRepository.save(CreatorDiscoveryInfo.builder()
                .creatorPool(withImage).profileImageUrl("https://example.com/profile.jpg").build());
        infoRepository.save(CreatorDiscoveryInfo.builder().creatorPool(withoutImage).build());
        em.flush();
        em.clear();

        assertThat(creatorPoolRepository.findDeletedDemoCandidatesWithProfileImage(
                List.of("YOUTUBE", "INSTAGRAM")))
                .extracting(CreatorPool::getAccountId)
                .containsExactly("UC_photo");
    }

    @Test
    @DisplayName("카테고리 데모 대상은 사진 있는 그 분야 계정을 노출 여부와 무관하게 조회한다")
    void findDemoCandidatesByCategory() {
        CreatorPool hidden = saveCategorizedCreator("UC_living_hidden", "LIVING_LIFE");
        CreatorPool visible = saveCategorizedCreator("UC_living_visible", "LIVING_LIFE");
        CreatorPool withoutImage = saveCategorizedCreator("UC_living_no_photo", "LIVING_LIFE");
        CreatorPool otherCategory = saveCategorizedCreator("UC_beauty", "BEAUTY");
        hidden.softDelete();
        for (CreatorPool creator : List.of(hidden, visible, otherCategory)) {
            infoRepository.save(CreatorDiscoveryInfo.builder()
                    .creatorPool(creator)
                    .profileImageUrl("https://example.com/profile.jpg").build());
        }
        infoRepository.save(CreatorDiscoveryInfo.builder().creatorPool(withoutImage).build());
        em.flush();
        em.clear();

        assertThat(creatorPoolRepository.findDemoCandidatesByCategory(
                "LIVING_LIFE", List.of("YOUTUBE", "INSTAGRAM")))
                .extracting(CreatorPool::getAccountId)
                .containsExactlyInAnyOrder("UC_living_hidden", "UC_living_visible");
    }

    @Test
    @DisplayName("발굴 이력 유무를 키워드·카테고리 단위로 확인한다")
    void existsByKeywordAndCategory() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        DiscoveryKeyword used = saveKeyword("FITNESS", "피트니스", "홈트레이닝");
        DiscoveryKeyword unused = saveKeyword("BEAUTY", "뷰티", "grwm");
        sourceRepository.save(CreatorDiscoverySource.builder()
                .creatorPool(creator).keyword(used)
                .viewShare(new BigDecimal("1.00000")).build());
        em.flush();
        em.clear();

        assertThat(sourceRepository.existsByKeywordId(used.getId())).isTrue();
        assertThat(sourceRepository.existsByKeywordId(unused.getId())).isFalse();
        assertThat(sourceRepository.existsByKeywordCategoryId(used.getCategory().getId())).isTrue();
        assertThat(sourceRepository.existsByKeywordCategoryId(unused.getCategory().getId()))
                .isFalse();
    }

    @Test
    @DisplayName("발굴 출처가 없으면 대표 카테고리 후보도 없다")
    void categorySharesEmptyWhenNoSource() {
        CreatorPool creator = saveCreator("YOUTUBE", "UC_fit01", "핏지피티 홈트");
        em.flush();
        em.clear();

        assertThat(sourceRepository.findCategoryShares(creator.getId())).isEmpty();
    }

    @Test
    @DisplayName("풀 초기화는 파생 데이터만 지우고 중심 행은 소프트 삭제한다")
    void resetCreatorPoolData() {
        CreatorPool active = saveCreator("YOUTUBE", "UC_active", "활성 계정");
        CreatorPool alreadyDeleted = saveCreator("INSTAGRAM", "ig_deleted", "기존 삭제 계정");
        alreadyDeleted.softDelete();
        DiscoveryKeyword keyword = saveKeyword("BEAUTY", "뷰티", "메이크업");
        for (CreatorPool creator : List.of(active, alreadyDeleted)) {
            infoRepository.save(CreatorDiscoveryInfo.builder()
                    .creatorPool(creator).brandScore(0).build());
            sourceRepository.save(CreatorDiscoverySource.builder()
                    .creatorPool(creator).keyword(keyword)
                    .viewShare(BigDecimal.ONE).build());
        }
        em.flush();
        em.clear();

        assertThat(sourceRepository.deleteAllByCreatorPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"))).isEqualTo(2);
        assertThat(infoRepository.deleteAllByCreatorPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"))).isEqualTo(2);
        assertThat(creatorPoolRepository.softDeleteAllActiveByPlatforms(
                List.of("YOUTUBE", "INSTAGRAM"))).isEqualTo(1);
        em.clear();

        assertThat(sourceRepository.count()).isZero();
        assertThat(infoRepository.count()).isZero();
        assertThat(creatorPoolRepository.findAll()).hasSize(2).allMatch(CreatorPool::isDeleted);
    }

    @TestConfiguration
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
