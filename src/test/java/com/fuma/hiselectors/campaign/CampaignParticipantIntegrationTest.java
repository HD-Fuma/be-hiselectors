package com.fuma.hiselectors.campaign;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.model.CampaignStatus;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.productgroup.model.ProductGroup;
import com.fuma.hiselectors.productgroup.model.ProductGroupItem;
import com.fuma.hiselectors.productgroup.repository.ProductGroupItemRepository;
import com.fuma.hiselectors.productgroup.repository.ProductGroupRepository;
import com.fuma.hiselectors.security.jwt.JwtTokenProvider;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:campaign-participants;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "jwt.secret=campaign-participants-test-secret-campaign-participants-test-secret",
        "discovery.defaults.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampaignParticipantIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private ProductGroupRepository productGroupRepository;
    @Autowired private ProductGroupItemRepository productGroupItemRepository;
    @Autowired private SelectorsRepository selectorsRepository;
    @Autowired private SelectorsSnsAccountRepository snsAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        productGroupItemRepository.deleteAllInBatch();
        productGroupRepository.deleteAllInBatch();
        snsAccountRepository.deleteAllInBatch();
        selectorsRepository.deleteAllInBatch();
        campaignRepository.deleteAllInBatch();
    }

    @Test
    void admin_gets_each_matching_selector_once_for_inclusive_campaign_dates() throws Exception {
        Campaign campaign = campaign("참여자", LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 19));
        Selectors selector = selector("정상참여자");
        snsAccountRepository.save(SelectorsSnsAccount.builder().selectorsId(selector.getId())
                .snsCode(SnsPlatform.INSTAGRAM).accountId("selector.ig").followerCount(1234L).build());
        ProductGroup group = productGroupRepository.save(new ProductGroup(selector.getId(), campaign.getId()));
        item(group, LocalDateTime.of(2026, 8, 18, 0, 0), false);
        Selectors softDeletedItemSelector = selector("삭제아이템");
        ProductGroup softDeletedItemGroup = productGroupRepository.save(
                new ProductGroup(softDeletedItemSelector.getId(), campaign.getId()));
        item(softDeletedItemGroup, LocalDateTime.of(2026, 8, 19, 23, 59, 59), true);
        Selectors nullTimeSelector = selector("null타임스탬프");
        ProductGroup nullTimeGroup = productGroupRepository.save(
                new ProductGroup(nullTimeSelector.getId(), campaign.getId()));
        ProductGroupItem nullTimeItem = item(nullTimeGroup, LocalDateTime.of(2026, 8, 18, 12, 0), false);
        jdbcTemplate.update("update product_group_item set created_at = null where product_group_item_id = ?", nullTimeItem.getId());
        Selectors outsideSelector = selector("기간외");
        ProductGroup outsideGroup = productGroupRepository.save(new ProductGroup(outsideSelector.getId(), campaign.getId()));
        item(outsideGroup, LocalDateTime.of(2026, 8, 20, 0, 0), false);
        Selectors deletedGroupSelector = selector("삭제그룹");
        ProductGroup deletedGroup = productGroupRepository.save(
                new ProductGroup(deletedGroupSelector.getId(), campaign.getId(), true));
        item(deletedGroup, LocalDateTime.of(2026, 8, 18, 10, 0), false);
        Selectors duplicateGroupSelector = selector("중복그룹참여자");
        ProductGroup firstDuplicateGroup = productGroupRepository.save(
                new ProductGroup(duplicateGroupSelector.getId(), campaign.getId()));
        ProductGroup secondDuplicateGroup = productGroupRepository.save(
                new ProductGroup(duplicateGroupSelector.getId(), campaign.getId()));
        item(firstDuplicateGroup, LocalDateTime.of(2026, 8, 18, 11, 0), false);
        item(secondDuplicateGroup, LocalDateTime.of(2026, 8, 18, 12, 0), false);

        mockMvc.perform(get("/api/admin/campaigns/{campaignId}/participants", campaign.getId())
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].selectorId").value(selector.getId()))
                .andExpect(jsonPath("$.data.content[0].nickname").value("정상참여자"))
                .andExpect(jsonPath("$.data.content[0].platform").value("INSTAGRAM"))
                .andExpect(jsonPath("$.data.content[0].accountId").value("selector.ig"))
                .andExpect(jsonPath("$.data.content[0].followerCount").value(1234))
                .andExpect(jsonPath("$.data.content[?(@.nickname == '삭제아이템')]").isNotEmpty())
                .andExpect(jsonPath("$.data.content[?(@.nickname == '삭제그룹')]").isNotEmpty())
                .andExpect(jsonPath("$.data.content[?(@.nickname == '중복그룹참여자')]", hasSize(1)))
                .andExpect(jsonPath("$.data.content[?(@.nickname == 'null타임스탬프')]").isEmpty());
    }

    @Test
    void participants_endpoint_requires_admin_and_hides_missing_or_deleted_campaigns() throws Exception {
        Campaign campaign = campaign("삭제", LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 18));
        mockMvc.perform(get("/api/admin/campaigns/{campaignId}/participants", campaign.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/campaigns/{campaignId}/participants", campaign.getId())
                        .header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns/999999/participants").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
        campaign.softDelete();
        campaignRepository.save(campaign);
        mockMvc.perform(get("/api/admin/campaigns/{campaignId}/participants", campaign.getId())
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
    }

    private Campaign campaign(String title, LocalDate start, LocalDate end) {
        return campaignRepository.save(Campaign.builder().title(title).description("설명")
                .startDate(start).endDate(end).status(CampaignStatus.SCHEDULED).build());
    }

    private Selectors selector(String nickname) {
        return selectorsRepository.save(Selectors.builder().selectorsRoleId("SELECTOR")
                .selectorsCode("CODE-" + nickname).selectorsNickname(nickname).build());
    }

    private ProductGroupItem item(ProductGroup group, LocalDateTime createdAt, boolean deleted) {
        ProductGroupItem item = productGroupItemRepository.save(
                new ProductGroupItem(group.getId(), 100L, (short) 1, deleted));
        jdbcTemplate.update("update product_group_item set created_at = ? where product_group_item_id = ?", createdAt, item.getId());
        return item;
    }

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.createToken("campaign-participants", role);
    }
}
