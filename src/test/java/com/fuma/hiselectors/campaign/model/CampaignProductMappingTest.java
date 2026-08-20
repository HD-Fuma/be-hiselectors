package com.fuma.hiselectors.campaign.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class CampaignProductMappingTest {

    @Test
    void maps_id_to_campaign_product_id_column() throws NoSuchFieldException {
        Field id = CampaignProduct.class.getDeclaredField("id");

        assertThat(id.getAnnotation(Column.class).name()).isEqualTo("campaign_product_id");
    }
}
