package com.fuma.hiselectors.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.model.ProductStatus;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.security.jwt.JwtTokenProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:campaign-query;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "jwt.secret=campaign-query-test-secret-campaign-query-test-secret",
        "jwt.access-token-validity-seconds=3600",
        "discovery.defaults.enabled=false"
})
@AutoConfigureMockMvc
@ContextConfiguration(classes = CampaignQueryIntegrationTest.FixedClockConfiguration.class)
@ActiveProfiles("test")
class CampaignQueryIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignProductRepository campaignProductRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MutableClock clock;

    @BeforeEach
    void setUp() {
        campaignProductRepository.deleteAllInBatch();
        campaignRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        clock.setInstant(Instant.parse("2026-08-18T00:00:00Z"));
    }

    @Test
    void list_is_authenticated_for_normal_users_and_returns_every_non_deleted_campaign_with_derived_status() throws Exception {
        Campaign scheduled = campaign("예정", TODAY.plusDays(1), TODAY.plusDays(2));
        Campaign active = campaign("진행", TODAY, TODAY);
        Campaign ended = campaign("종료", TODAY.minusDays(2), TODAY.minusDays(1));
        Campaign deleted = campaign("삭제", TODAY.minusDays(3), TODAY.minusDays(2));
        deleted.softDelete();
        campaignRepository.saveAll(java.util.List.of(scheduled, active, ended, deleted));

        mockMvc.perform(get("/api/campaigns"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/campaigns").header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.id == %s)].status", scheduled.getId()).value("SCHEDULED"))
                .andExpect(jsonPath("$.data[?(@.id == %s)].status", active.getId()).value("ACTIVE"))
                .andExpect(jsonPath("$.data[?(@.id == %s)].status", ended.getId()).value("ENDED"));
    }

    @Test
    void detail_returns_campaign_product_display_data_even_when_linked_product_is_no_longer_available() throws Exception {
        Product soldOut = productRepository.save(product("P-001", "상품", "브랜드", ProductStatus.SOLD_OUT));
        Campaign campaign = campaignRepository.save(campaign("상세", TODAY, TODAY));
        campaignProductRepository.save(new CampaignProduct(campaign, soldOut));

        mockMvc.perform(get("/api/campaigns/{id}", campaign.getId()).header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(campaign.getId()))
                .andExpect(jsonPath("$.data.title").value("상세"))
                .andExpect(jsonPath("$.data.description").value("설명"))
                .andExpect(jsonPath("$.data.startDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.endDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("campaign-thumb"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.products[0].id").value(soldOut.getId()))
                .andExpect(jsonPath("$.data.products[0].code").value("P-001"))
                .andExpect(jsonPath("$.data.products[0].name").value("상품"))
                .andExpect(jsonPath("$.data.products[0].brand").value("브랜드"))
                .andExpect(jsonPath("$.data.products[0].category").value("카테고리"))
                .andExpect(jsonPath("$.data.products[0].regularPrice").value(10000))
                .andExpect(jsonPath("$.data.products[0].salePrice").value(8000))
                .andExpect(jsonPath("$.data.products[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.products[0].thumbnailUrl").value("product-thumb"))
                .andExpect(jsonPath("$.data.products[0].detailUrl").value("product-detail"));
    }

    @Test
    void list_deduplicates_nonblank_brands_and_detail_keeps_empty_product_links_empty() throws Exception {
        Product first = productRepository.save(product("P-101", "첫", " 브랜드A ", ProductStatus.ON_SALE));
        Product duplicate = productRepository.save(product("P-102", "둘", "브랜드A", ProductStatus.ON_SALE));
        Product blank = productRepository.save(product("P-103", "셋", "  ", ProductStatus.ON_SALE));
        Campaign linked = campaignRepository.save(campaign("브랜드", TODAY, TODAY));
        Campaign empty = campaignRepository.save(campaign("빈 연결", TODAY, TODAY));
        campaignProductRepository.save(new CampaignProduct(linked, first));
        campaignProductRepository.save(new CampaignProduct(linked, duplicate));
        campaignProductRepository.save(new CampaignProduct(linked, blank));

        mockMvc.perform(get("/api/campaigns").header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %s)].brands.length()", linked.getId()).value(1))
                .andExpect(jsonPath("$.data[?(@.id == %s)].brands[0]", linked.getId()).value("브랜드A"));
        mockMvc.perform(get("/api/campaigns/{id}", empty.getId()).header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products").isEmpty());
    }

    @Test
    void detail_returns_not_found_for_missing_or_soft_deleted_campaign_and_uses_clock_after_rollover() throws Exception {
        Campaign rollover = campaignRepository.save(campaign("롤오버", TODAY.plusDays(1), TODAY.plusDays(1)));
        Campaign deleted = campaignRepository.save(campaign("삭제", TODAY.minusDays(2), TODAY.minusDays(1)));
        deleted.softDelete();
        campaignRepository.save(deleted);
        clock.setInstant(Instant.parse("2026-08-19T00:00:00Z"));

        mockMvc.perform(get("/api/campaigns/{id}", rollover.getId()).header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(get("/api/campaigns/999999").header("Authorization", bearer("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
        mockMvc.perform(get("/api/campaigns/{id}", deleted.getId()).header("Authorization", bearer("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
    }

    private Campaign campaign(String title, LocalDate startDate, LocalDate endDate) {
        return Campaign.builder().title(title).description("설명").startDate(startDate).endDate(endDate)
                .thumbnailUrl("campaign-thumb").build();
    }

    private Product product(String code, String name, String brand, ProductStatus status) {
        return Product.builder().productCode(code).productName(name).brandName(brand).category("카테고리")
                .regularPrice(new BigDecimal("10000.00")).salePrice(new BigDecimal("8000.00"))
                .status(status).thumbnailUrl("product-thumb").detailUrl("product-detail").build();
    }

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.createToken("campaign-user", role);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary MutableClock clock() {
            return new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        void setInstant(Instant instant) { this.instant.set(instant); }
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
