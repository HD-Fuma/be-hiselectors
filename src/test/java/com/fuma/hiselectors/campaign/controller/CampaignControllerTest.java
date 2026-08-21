package com.fuma.hiselectors.campaign.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.campaign.dto.CampaignDetailResponse;
import com.fuma.hiselectors.campaign.service.CampaignClientService;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CampaignControllerTest {

    @Test
    void bothQueriesRequireCurrentSelectorAccessBeforeReadingCampaigns() {
        CampaignClientService campaignService = mock(CampaignClientService.class);
        SelectorAccessService accessService = mock(SelectorAccessService.class);
        CampaignController controller = new CampaignController(campaignService, accessService);
        Principal principal = () -> "hi-user";
        CampaignDetailResponse detail = mock(CampaignDetailResponse.class);
        when(campaignService.findAll()).thenReturn(List.of());
        when(campaignService.findOne(3L)).thenReturn(detail);

        assertThat(controller.findAll(principal).getBody()).isEmpty();
        assertThat(controller.findOne(3L, principal).getBody()).isSameAs(detail);

        InOrder order = inOrder(accessService, campaignService);
        order.verify(accessService).requireCurrent("hi-user");
        order.verify(campaignService).findAll();
        order.verify(accessService).requireCurrent("hi-user");
        order.verify(campaignService).findOne(3L);
    }
}
