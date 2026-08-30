package com.fuma.hiselectors.inspection.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfiguredAffiliateLinkValidatorTest {

    private final ConfiguredAffiliateLinkValidator validator =
            new ConfiguredAffiliateLinkValidator(new ContentInspectionProperties(
                    null, null, "ptrsRefCd"));

    @ParameterizedTest
    @ValueSource(strings = {
            "https://hi.thehyundai.com/shop/RC000005105T",
            "https://hiselectors.shop/product/40A2125547?ptrsRefCd=RC000005203T",
            "https://hi.thehyundai.com/sellectors/manage/shop/RC000005105T",
            "https://hiselectors.shop/product/_",
            "https://hi.thehyundai.com/shop/레퍼럴-코드"
    })
    void acceptsSupportedLinkShapes(String url) {
        assertThat(validator.isValid(url)).isTrue();
    }

    @Test
    void rejectsHostOutsideAllowedHosts() {
        assertThat(validator.isValid("https://example.com/product/40A2125547")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://hiselectors.shop/",
            "https://hiselectors.shop/product/",
            "https://hiselectors.shop/product/40A2125547/extra",
            "https://hi.thehyundai.com/sellectors/manage/shop/",
            "https://hi.thehyundai.com/category/000001"
    })
    void rejectsUnsupportedLinkShapes(String url) {
        assertThat(validator.isValid(url)).isFalse();
    }
}
