package com.fuma.hiselectors.creator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.config.JpaAuditingConfig;
import com.fuma.hiselectors.creator.dto.CreatorSummary;
import com.fuma.hiselectors.creator.dto.InfluenceCandidate;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorPool;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Import;

/**
 * 조회 조건 필터링 검증.
 *
 * <p>발굴 결과는 수집 시점에 걸러내지 않고 전부 저장하므로, 브랜드 계정이나
 * 구독자 미달 계정을 빼는 일은 전적으로 이 쿼리가 담당한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class CreatorPoolSearchTest {

    @Autowired
    private CreatorPoolRepository creatorPoolRepository;

    @Autowired
    private TestEntityManager em;

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        // 정상 크리에이터 — URL 로 IG 핸들을 찾음
        save("YOUTUBE", "UC_fit01", "핏지피티 홈트", 412_000L, "FITNESS", 3,
                0, "fitgpt_daily", "0.95");

        // 브랜드 계정 — 브랜드 신호 5점
        save("YOUTUBE", "UC_brand01", "무신사 공식채널", 1_200_000L, "FASHION", 5,
                5, "musinsa.official", "0.95");

        // 구독자 미달
        save("YOUTUBE", "UC_small01", "운동일기 초보", 1_200L, "FITNESS", 10,
                0, "gym_newbie_log", "0.35");

        // 휴면 채널 — 400일 전 활동
        save("YOUTUBE", "UC_old01", "헬스마스터TV", 260_000L, "FITNESS", 400,
                0, "health_master_tv", "0.95");

        em.flush();
        em.clear();
    }

    private void save(String snsCode, String accountId, String name, Long followers,
                      String category, int daysSinceContent,
                      int brandScore, String igHandle, String igConfidence) {
        CreatorPool creator = creatorPoolRepository.save(CreatorPool.builder()
                .snsCode(snsCode).accountId(accountId).creatorName(name)
                .followerCount(followers).category(category)
                .engagementRate(new BigDecimal("4.00"))
                .lastContentAt(LocalDateTime.now().minusDays(daysSinceContent))
                .build());
        em.persist(CreatorDiscoveryInfo.builder()
                .creatorPool(creator)
                .brandScore(brandScore)
                .igHandle(igHandle)
                .igConfidence(new BigDecimal(igConfidence))
                .build());
    }

    @Test
    @DisplayName("조건을 비우면 전부 조회된다")
    void searchWithoutCondition() {
        Page<CreatorSummary> result =
                creatorPoolRepository.search(null, null, null, null, null, null, FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("브랜드 점수 상한으로 브랜드 계정을 제외한다")
    void excludeBrandAccounts() {
        Page<CreatorSummary> result =
                creatorPoolRepository.search(null, null, null, 1, null, null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .doesNotContain("무신사 공식채널")
                .hasSize(3);
    }

    @Test
    @DisplayName("영향력 후보는 카테고리·플랫폼·브랜드·최근 활동 조건을 모두 만족해야 한다")
    void findInfluenceCandidates() {
        save("YOUTUBE", "UC_fitness_brand", "피트니스 공식", 900_000L,
                "FITNESS", 1, 5, null, "0.95");
        save("INSTAGRAM", "ig_fitness", "인스타 운동", 80_000L,
                "FITNESS", 1, 0, null, "0.95");
        em.flush();
        em.clear();

        List<InfluenceCandidate> result = creatorPoolRepository
                .findInfluenceCandidates(
                        "FITNESS", "YOUTUBE", 1, LocalDateTime.now().minusDays(90));

        assertThat(result).extracting(InfluenceCandidate::creatorName)
                .containsExactlyInAnyOrder("핏지피티 홈트", "운동일기 초보")
                .doesNotContain("헬스마스터TV", "피트니스 공식", "인스타 운동");
    }

    @Test
    @DisplayName("일일 후보 비교 풀은 같은 카테고리의 플랫폼들을 함께 조회한다")
    void findInfluenceCandidatesByCategory() {
        save("YOUTUBE", "UC_fitness_brand", "피트니스 공식", 900_000L,
                "FITNESS", 1, 5, null, "0.95");
        save("INSTAGRAM", "ig_fitness", "인스타 운동", 80_000L,
                "FITNESS", 1, 0, null, "0.95");
        em.flush();
        em.clear();

        List<InfluenceCandidate> result = creatorPoolRepository
                .findInfluenceCandidatesByCategory(
                        "FITNESS", 1, LocalDateTime.now().minusDays(90));

        assertThat(result).extracting(InfluenceCandidate::creatorName)
                .containsExactlyInAnyOrder("핏지피티 홈트", "운동일기 초보", "인스타 운동")
                .doesNotContain("헬스마스터TV", "피트니스 공식");
        assertThat(result).allSatisfy(candidate ->
                assertThat(candidate.discoveredAt()).isNotNull());
        assertThat(result).allSatisfy(candidate ->
                assertThat(candidate.updatedAt()).isNotNull());
    }

    @Test
    @DisplayName("최소 구독자 기준으로 거른다")
    void filterByMinFollower() {
        Page<CreatorSummary> result =
                creatorPoolRepository.search(null, null, 5_000L, null, null, null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .doesNotContain("운동일기 초보")
                .hasSize(3);
    }

    @Test
    @DisplayName("IG 핸들 신뢰도로 거른다")
    void filterByIgConfidence() {
        // 0.95(URL) 로 찾은 것만 남기면 0.35(단순 멘션) 는 빠진다
        Page<CreatorSummary> result = creatorPoolRepository.search(
                null, null, null, null, new BigDecimal("0.95"), null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .doesNotContain("운동일기 초보")
                .hasSize(3);
    }

    @Test
    @DisplayName("최근 활동일로 휴면 채널을 제외한다")
    void filterByActivity() {
        LocalDateTime activeAfter = LocalDateTime.now().minusDays(180);
        Page<CreatorSummary> result =
                creatorPoolRepository.search(null, null, null, null, null, activeAfter, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .doesNotContain("헬스마스터TV")
                .hasSize(3);
    }

    @Test
    @DisplayName("카테고리로 거른다")
    void filterByCategory() {
        Page<CreatorSummary> result =
                creatorPoolRepository.search("FITNESS", null, null, null, null, null, FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
                .extracting(CreatorSummary::category)
                .containsOnly("FITNESS");
    }

    @Test
    @DisplayName("조건을 겹쳐 걸면 모두 만족하는 것만 남는다")
    void combinedConditions() {
        Page<CreatorSummary> result = creatorPoolRepository.search(
                "FITNESS", "YOUTUBE", 5_000L, 1, new BigDecimal("0.75"),
                LocalDateTime.now().minusDays(180), FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .containsExactly("핏지피티 홈트");
    }

    @Test
    @DisplayName("소프트 삭제된 계정은 조회되지 않는다")
    void excludeSoftDeleted() {
        CreatorPool creator = creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc("YOUTUBE", "UC_fit01").orElseThrow();
        creator.softDelete();
        em.flush();
        em.clear();

        Page<CreatorSummary> result =
                creatorPoolRepository.search(null, null, null, null, null, null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .doesNotContain("핏지피티 홈트");
    }

    @Test
    @DisplayName("발굴 정보가 없는 계정도 조회된다 (left join)")
    void includesCreatorWithoutDiscoveryInfo() {
        creatorPoolRepository.save(CreatorPool.builder()
                .snsCode("INSTAGRAM").accountId("manual_account").creatorName("수동 등록 계정")
                .followerCount(50_000L).category("BEAUTY")
                .lastContentAt(LocalDateTime.now()).build());
        em.flush();
        em.clear();

        Page<CreatorSummary> result =
                creatorPoolRepository.search(null, null, null, null, null, null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .contains("수동 등록 계정");

        // 발굴 정보가 없으면 판정 근거는 null 로 내려간다
        CreatorSummary manual = result.getContent().stream()
                .filter(c -> "수동 등록 계정".equals(c.creatorName()))
                .findFirst().orElseThrow();
        assertThat(manual.brandScore()).isNull();
        assertThat(manual.igHandle()).isNull();
    }

    @Test
    @DisplayName("브랜드 점수 조건을 걸면 발굴 정보 없는 계정은 0점으로 취급된다")
    void nullBrandScoreTreatedAsZero() {
        creatorPoolRepository.save(CreatorPool.builder()
                .snsCode("INSTAGRAM").accountId("manual_account").creatorName("수동 등록 계정")
                .followerCount(50_000L).category("BEAUTY")
                .lastContentAt(LocalDateTime.now()).build());
        em.flush();
        em.clear();

        Page<CreatorSummary> result =
                creatorPoolRepository.search(null, null, null, 1, null, null, FIRST_PAGE);

        assertThat(result.getContent())
                .extracting(CreatorSummary::creatorName)
                .contains("수동 등록 계정");
    }
}
