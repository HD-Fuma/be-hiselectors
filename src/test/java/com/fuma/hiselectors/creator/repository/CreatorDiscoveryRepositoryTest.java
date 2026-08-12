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

/**
 * 커스텀 JPQL 은 애플리케이션 기동 시점에 파싱된다. 문법이 틀리면 앱 자체가 뜨지 않으므로
 * 매핑과 쿼리를 여기서 먼저 검증한다.
 */
@DataJpaTest
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
}
