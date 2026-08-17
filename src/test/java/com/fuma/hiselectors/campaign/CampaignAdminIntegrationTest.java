package com.fuma.hiselectors.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.campaign.dto.CampaignCreateRequest;
import com.fuma.hiselectors.campaign.dto.CampaignUpdateRequest;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:campaign-admin;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "jwt.secret=campaign-admin-test-secret-campaign-admin-test-secret",
        "jwt.access-token-validity-seconds=3600",
        "discovery.defaults.enabled=false"
})
@AutoConfigureMockMvc
@ContextConfiguration(classes = CampaignAdminIntegrationTest.FixedClockConfiguration.class)
@ActiveProfiles("test")
class CampaignAdminIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignProductRepository campaignProductRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.setInstant(Instant.parse("2026-08-18T00:00:00Z"));
    }

    @Test
    void admin_can_create_search_update_and_soft_delete_ended_campaign() throws Exception {
        Product onSale = productRepository.save(product("P-1", ProductStatus.ON_SALE));
        Product soldOut = productRepository.save(product("P-2", ProductStatus.SOLD_OUT));

        CampaignCreateRequest create = new CampaignCreateRequest(
                "여름 캠페인", "설명", TODAY.plusDays(1), TODAY.plusDays(3), "thumb", List.of(onSale.getId()));

        String created = mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.productIds[0]").value(onSale.getId()))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).at("/data/id").asLong();

        mockMvc.perform(get("/api/admin/campaigns")
                        .header("Authorization", bearer("ADMIN"))
                        .param("keyword", "여름")
                        .param("startDate", TODAY.toString())
                        .param("endDate", TODAY.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(id))
                .andExpect(jsonPath("$.data.content[0].status").value("SCHEDULED"));

        CampaignUpdateRequest retainProducts = new CampaignUpdateRequest(
                "수정 캠페인", null, TODAY, TODAY, null, null);
        mockMvc.perform(patch("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retainProducts)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.productIds[0]").value(onSale.getId()));

        CampaignUpdateRequest replaceProducts = new CampaignUpdateRequest(
                null, null, TODAY.minusDays(2), TODAY.minusDays(1), null, List.of());
        mockMvc.perform(patch("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replaceProducts)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.productIds").isEmpty());

        mockMvc.perform(delete("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
        assertThat(campaignRepository.findById(id).orElseThrow().isDeleted()).isTrue();
        assertThat(campaignProductRepository.findAllByCampaignId(id)).isEmpty();

        mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest(
                                "실패", "설명", TODAY, TODAY, null, List.of(soldOut.getId())))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));
    }

    @Test
    void campaign_crud_requires_admin_role() throws Exception {
        for (var request : List.of(
                get("/api/admin/campaigns"),
                post("/api/admin/campaigns").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/admin/campaigns/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/admin/campaigns/1"))) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
            mockMvc.perform(request.header("Authorization", bearer("USER")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void status_is_derived_when_read_after_the_date_rolls_over() throws Exception {
        Long id = createCampaign("롤오버", TODAY.plusDays(1), TODAY.plusDays(1), List.of());

        clock.setInstant(Instant.parse("2026-08-19T00:00:00Z"));

        mockMvc.perform(get("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(get("/api/admin/campaigns").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"));
    }

    @Test
    void invalid_campaign_requests_return_400_and_product_errors_are_specific() throws Exception {
        Product onSale = productRepository.save(product("P-validation", ProductStatus.ON_SALE));
        Long id = createCampaign("검증", TODAY, TODAY, List.of(onSale.getId()));

        mockMvc.perform(post("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","description":"설명","startDate":"2026-08-18","endDate":"2026-08-18"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest(
                                "중복", "설명", TODAY, TODAY, null, List.of(onSale.getId(), onSale.getId())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(post("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest(
                                "없음", "설명", TODAY, TODAY, null, List.of(999999L)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(patch("/api/admin/campaigns/{id}", id).header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{" + "\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/campaigns/{id}", id).header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"startDate\":\"2026-08-19\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void active_campaign_cannot_be_deleted_and_deleted_campaign_cannot_be_read() throws Exception {
        Long activeId = createCampaign("활성", TODAY, TODAY, List.of());
        mockMvc.perform(delete("/api/admin/campaigns/{id}", activeId)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_DELETE_NOT_ALLOWED"));
        mockMvc.perform(get("/api/admin/campaigns/999999").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());

        Long endedId = createCampaign("삭제", TODAY.minusDays(2), TODAY.minusDays(1), List.of());
        mockMvc.perform(delete("/api/admin/campaigns/{id}", endedId)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/campaigns/{id}", endedId).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_and_patch_reject_values_larger_than_the_campaign_schema_limits() throws Exception {
        Long id = createCampaign("길이검증", TODAY, TODAY, List.of());

        mockMvc.perform(post("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest(
                                "t".repeat(101), "설명", TODAY, TODAY, null, List.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/campaigns/{id}", id).header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thumbnailUrl\":\"" + "u".repeat(401) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private Long createCampaign(String title, LocalDate startDate, LocalDate endDate, List<Long> productIds)
            throws Exception {
        String response = mockMvc.perform(post("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest(
                                title, "설명", startDate, endDate, null, productIds))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Product product(String code, ProductStatus status) {
        return Product.builder().productCode(code)
                .regularPrice(BigDecimal.TEN).salePrice(BigDecimal.ONE).status(status).build();
    }

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.createToken("campaign-admin", role);
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        MutableClock clock() {
            return new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        void setInstant(Instant instant) {
            this.instant.set(instant);
        }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
