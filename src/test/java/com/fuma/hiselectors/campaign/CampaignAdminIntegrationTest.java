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
import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.campaign.service.CampaignThumbnailRemovalRequested;
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
import java.util.Set;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
@RecordApplicationEvents
class CampaignAdminIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private CampaignProductRepository campaignProductRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private MutableClock clock;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApplicationEvents applicationEvents;

    @BeforeEach
    void resetClock() {
        campaignProductRepository.deleteAllInBatch();
        campaignRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
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
        assertThat(campaignProductRepository.findAllByCampaignIdOrderByIdAsc(id)).isEmpty();

        mockMvc.perform(post("/api/admin/campaigns")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest(
                                "실패", "설명", TODAY, TODAY, null, List.of(soldOut.getId())))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));
    }

    @Test
    void patch_keeps_thumbnail_when_removal_and_url_fields_are_omitted() throws Exception {
        String originalUrl = managedThumbnailUrl("123e4567-e89b-12d3-a456-426614174000", "png");
        Long id = createCampaignWithThumbnail(originalUrl);

        mockMvc.perform(patch("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목 변경\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value(originalUrl));

        assertThat(applicationEvents.stream(CampaignThumbnailRemovalRequested.class)).isEmpty();
    }

    @Test
    void patch_remove_thumbnail_clears_url_and_wins_over_replacement_url() throws Exception {
        String originalUrl = managedThumbnailUrl("123e4567-e89b-12d3-a456-426614174000", "png");
        String replacementUrl = managedThumbnailUrl("123e4567-e89b-12d3-a456-426614174001", "webp");
        Long id = createCampaignWithThumbnail(originalUrl);

        mockMvc.perform(patch("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"thumbnailUrl":"%s","removeThumbnail":true}
                                """.formatted(replacementUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").isEmpty());

        assertThat(campaignRepository.findById(id).orElseThrow().getThumbnailUrl()).isNull();
        assertThat(applicationEvents.stream(CampaignThumbnailRemovalRequested.class)
                .map(CampaignThumbnailRemovalRequested::url))
                .containsExactly(originalUrl, replacementUrl);
    }

    @Test
    void patch_remove_thumbnail_publishes_duplicate_cleanup_url_once() throws Exception {
        String thumbnailUrl = managedThumbnailUrl("123e4567-e89b-12d3-a456-426614174000", "png");
        Long id = createCampaignWithThumbnail(thumbnailUrl);

        mockMvc.perform(patch("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"thumbnailUrl":"%s","removeThumbnail":true}
                                """.formatted(thumbnailUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").isEmpty());

        assertThat(applicationEvents.stream(CampaignThumbnailRemovalRequested.class)
                .map(CampaignThumbnailRemovalRequested::url)).containsExactly(thumbnailUrl);
    }

    @Test
    void patch_replacement_publishes_previous_thumbnail_removal() throws Exception {
        String originalUrl = managedThumbnailUrl("123e4567-e89b-12d3-a456-426614174000", "jpg");
        String replacementUrl = managedThumbnailUrl("123e4567-e89b-12d3-a456-426614174001", "png");
        Long id = createCampaignWithThumbnail(originalUrl);

        mockMvc.perform(patch("/api/admin/campaigns/{id}", id)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thumbnailUrl\":\"" + replacementUrl + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value(replacementUrl));

        assertThat(applicationEvents.stream(CampaignThumbnailRemovalRequested.class)
                .map(CampaignThumbnailRemovalRequested::url)).containsExactly(originalUrl);
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
    void campaign_date_search_includes_campaigns_touching_either_query_boundary() throws Exception {
        Long touchesStart = createCampaign("경계 시작", TODAY.minusDays(2), TODAY, List.of());
        Long touchesEnd = createCampaign("경계 종료", TODAY.plusDays(2), TODAY.plusDays(4), List.of());
        createCampaign("범위 이전", TODAY.minusDays(3), TODAY.minusDays(1), List.of());
        createCampaign("범위 이후", TODAY.plusDays(3), TODAY.plusDays(5), List.of());

        mockMvc.perform(get("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .param("startDate", TODAY.toString())
                        .param("endDate", TODAY.plusDays(2).toString())
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(touchesEnd))
                .andExpect(jsonPath("$.data.content[1].id").value(touchesStart));
    }

    @Test
    void campaign_status_filter_uses_seoul_today_and_combines_with_search_and_page_predicates()
            throws Exception {
        clock.setInstant(Instant.parse("2026-08-17T15:30:00Z"));
        createCampaign("검색 예정", TODAY.plusDays(1), TODAY.plusDays(2), List.of());
        createCampaign("검색 진행 첫째", TODAY.minusDays(1), TODAY.plusDays(1), List.of());
        Long secondActiveId = createCampaign("검색 진행 둘째", TODAY, TODAY, List.of());
        createCampaign("검색 종료", TODAY.minusDays(2), TODAY.minusDays(1), List.of());
        createCampaign("다른 진행", TODAY, TODAY.plusDays(1), List.of());

        mockMvc.perform(get("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("SCHEDULED"));
        mockMvc.perform(get("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .param("status", "ENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("ENDED"));
        mockMvc.perform(get("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .param("status", "ACTIVE")
                        .param("keyword", "검색")
                        .param("startDate", TODAY.minusDays(1).toString())
                        .param("endDate", TODAY.plusDays(1).toString())
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(secondActiveId))
                .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"));
    }

    @Test
    void campaign_response_contains_product_ids_and_product_details_in_association_order()
            throws Exception {
        Product full = productRepository.save(Product.builder()
                .productCode("P-full").productName("상품명").brandName("브랜드").category("카테고리")
                .regularPrice(new BigDecimal("12000.00")).salePrice(new BigDecimal("9900.00"))
                .status(ProductStatus.ON_SALE).thumbnailUrl("https://example.com/product.jpg")
                .detailUrl("https://example.com/product").build());
        Product nullable = productRepository.save(Product.builder()
                .productCode("P-nullable").regularPrice(new BigDecimal("20000.00"))
                .salePrice(new BigDecimal("15000.00")).status(ProductStatus.ON_SALE).build());
        Long campaignId = createCampaign("상품 응답", TODAY, TODAY, List.of());
        Campaign campaign = campaignRepository.findById(campaignId).orElseThrow();
        campaignProductRepository.saveAndFlush(new CampaignProduct(campaign, nullable));
        campaignProductRepository.saveAndFlush(new CampaignProduct(campaign, full));

        String response = mockMvc.perform(get("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productIds[0]").value(nullable.getId()))
                .andExpect(jsonPath("$.data.productIds[1]").value(full.getId()))
                .andExpect(jsonPath("$.data.products[0].id").value(nullable.getId()))
                .andExpect(jsonPath("$.data.products[0].code").value("P-nullable"))
                .andExpect(jsonPath("$.data.products[0].productName").isEmpty())
                .andExpect(jsonPath("$.data.products[0].brandName").isEmpty())
                .andExpect(jsonPath("$.data.products[0].category").isEmpty())
                .andExpect(jsonPath("$.data.products[0].regularPrice").value(20000))
                .andExpect(jsonPath("$.data.products[0].salePrice").value(15000))
                .andExpect(jsonPath("$.data.products[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.products[0].thumbnailUrl").isEmpty())
                .andExpect(jsonPath("$.data.products[0].detailUrl").isEmpty())
                .andExpect(jsonPath("$.data.products[1].id").value(full.getId()))
                .andExpect(jsonPath("$.data.products[1].code").value("P-full"))
                .andExpect(jsonPath("$.data.products[1].productName").value("상품명"))
                .andExpect(jsonPath("$.data.products[1].brandName").value("브랜드"))
                .andExpect(jsonPath("$.data.products[1].category").value("카테고리"))
                .andExpect(jsonPath("$.data.products[1].regularPrice").value(12000))
                .andExpect(jsonPath("$.data.products[1].salePrice").value(9900))
                .andExpect(jsonPath("$.data.products[1].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.products[1].thumbnailUrl")
                        .value("https://example.com/product.jpg"))
                .andExpect(jsonPath("$.data.products[1].detailUrl").value("https://example.com/product"))
                .andReturn().getResponse().getContentAsString();

        assertThat(Set.copyOf(objectMapper.readTree(response).at("/data/products/0")
                .properties().stream().map(java.util.Map.Entry::getKey).toList()))
                .containsExactlyInAnyOrder("id", "code", "productName", "brandName", "category",
                        "regularPrice", "salePrice", "status", "thumbnailUrl", "detailUrl");

        mockMvc.perform(get("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .param("keyword", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productIds[0]").value(nullable.getId()))
                .andExpect(jsonPath("$.data.content[0].products[0].id").value(nullable.getId()))
                .andExpect(jsonPath("$.data.content[0].productIds[1]").value(full.getId()))
                .andExpect(jsonPath("$.data.content[0].products[1].id").value(full.getId()));
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
                        .content("""
                                {"title":"제목","description":" ","startDate":"2026-08-18","endDate":"2026-08-18"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"제목","description":"설명","startDate":"2026-08-19","endDate":"2026-08-18"}
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
                        .contentType(MediaType.APPLICATION_JSON).content("{" + "\"description\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/campaigns/{id}", id).header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"startDate\":\"2026-08-19\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/campaigns").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignCreateRequest(
                                "제목", "설명", TODAY, TODAY, "   ", List.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/campaigns/{id}", id).header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"thumbnailUrl\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_allows_retaining_or_removing_unavailable_links_but_rejects_new_unavailable_links()
            throws Exception {
        Product retained = productRepository.save(product("P-retained", ProductStatus.ON_SALE));
        Product newlyUnavailable = productRepository.save(product("P-new-unavailable", ProductStatus.SOLD_OUT));
        Long campaignId = createCampaign("상품 변경", TODAY, TODAY, List.of(retained.getId()));
        jdbcTemplate.update("update product set status = 'SOLD_OUT' where product_id = ?", retained.getId());

        mockMvc.perform(patch("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignUpdateRequest(
                                null, null, null, null, null, List.of(retained.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productIds[0]").value(retained.getId()));

        mockMvc.perform(patch("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignUpdateRequest(
                                null, null, null, null, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productIds").isEmpty());

        mockMvc.perform(patch("/api/admin/campaigns/{id}", campaignId)
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CampaignUpdateRequest(
                                null, null, null, null, null, List.of(newlyUnavailable.getId())))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));
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

    @Test
    void admin_can_search_products_for_campaign_picker() throws Exception {
        Product codeMatch = productRepository.save(Product.builder()
                .productCode("CODE-ALPHA").productName("여름 모자").brandName("셀렉터스")
                .category("패션").regularPrice(new BigDecimal("50000.00"))
                .salePrice(new BigDecimal("35000.00")).status(ProductStatus.ON_SALE)
                .thumbnailUrl("https://example.com/thumb.jpg").detailUrl("https://example.com/detail")
                .build());
        productRepository.save(Product.builder()
                .productCode("CODE-BETA").productName("겨울 모자").brandName("다른 브랜드")
                .category("패션").regularPrice(BigDecimal.TEN).salePrice(BigDecimal.ONE)
                .status(ProductStatus.SOLD_OUT).build());
        for (int index = 0; index < 20; index++) {
            productRepository.save(Product.builder()
                    .productCode("PAGE-" + index).productName("페이지 상품 " + index)
                    .regularPrice(BigDecimal.TEN).salePrice(BigDecimal.ONE).status(ProductStatus.ON_SALE).build());
        }

        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer("ADMIN"))
                        .param("keyword", "ALPHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(codeMatch.getId()))
                .andExpect(jsonPath("$.data.content[0].code").value("CODE-ALPHA"))
                .andExpect(jsonPath("$.data.content[0].productName").value("여름 모자"))
                .andExpect(jsonPath("$.data.content[0].brandName").value("셀렉터스"))
                .andExpect(jsonPath("$.data.content[0].category").value("패션"))
                .andExpect(jsonPath("$.data.content[0].regularPrice").value(50000))
                .andExpect(jsonPath("$.data.content[0].salePrice").value(35000))
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl").value("https://example.com/thumb.jpg"))
                .andExpect(jsonPath("$.data.content[0].detailUrl").value("https://example.com/detail"));
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer("ADMIN"))
                        .param("keyword", "여름"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(codeMatch.getId()));
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer("ADMIN"))
                        .param("keyword", "BETA")
                        .param("status", "SOLD_OUT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("SOLD_OUT"));
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content.length()").value(20));

        mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void product_keyword_treats_like_wildcards_as_literals() throws Exception {
        Product percentProduct = productRepository.save(Product.builder()
                .productCode("CODE%LITERAL").productName("일반 상품")
                .regularPrice(BigDecimal.TEN).salePrice(BigDecimal.ONE).status(ProductStatus.ON_SALE).build());
        Product underscoreProduct = productRepository.save(Product.builder()
                .productCode("NORMAL").productName("이름_LITERAL")
                .regularPrice(BigDecimal.TEN).salePrice(BigDecimal.ONE).status(ProductStatus.ON_SALE).build());
        productRepository.save(Product.builder()
                .productCode("CODEXLITERAL").productName("이름XLITERAL")
                .regularPrice(BigDecimal.TEN).salePrice(BigDecimal.ONE).status(ProductStatus.ON_SALE).build());

        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer("ADMIN"))
                        .param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(percentProduct.getId()));
        mockMvc.perform(get("/api/admin/products").header("Authorization", bearer("ADMIN"))
                        .param("keyword", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(underscoreProduct.getId()));
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

    private Long createCampaignWithThumbnail(String thumbnailUrl) {
        return campaignRepository.saveAndFlush(Campaign.builder()
                .title("썸네일 캠페인").description("설명")
                .startDate(TODAY).endDate(TODAY).thumbnailUrl(thumbnailUrl).build()).getId();
    }

    private String managedThumbnailUrl(String uuid, String extension) {
        return "https://media.hiselectors.shop/campaigns/" + uuid + "." + extension;
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
