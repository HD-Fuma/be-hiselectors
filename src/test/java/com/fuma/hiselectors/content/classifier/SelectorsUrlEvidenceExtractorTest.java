package com.fuma.hiselectors.content.classifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SelectorsUrlEvidenceExtractorTest {

    @Test
    void extractsDistinctSortedUrlsAndMasksTheirText() {
        String text = "앞 https://hi.thehyundai.com/product/A... 뒤\n"
                + "https://hi.thehyundai.com/product/A\n"
                + "https://hi.thehyundai.com/product/B)]";

        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(text);

        assertThat(result.matchedUrls()).containsExactly(
                "https://hi.thehyundai.com/product/A",
                "https://hi.thehyundai.com/product/B");
        assertThat(result.textWithoutUrls()).isEqualTo(
                "앞 " + " ".repeat("https://hi.thehyundai.com/product/A".length()) + "... 뒤\n"
                        + " ".repeat("https://hi.thehyundai.com/product/A".length()) + "\n"
                        + " ".repeat("https://hi.thehyundai.com/product/B".length()) + ")]" );
    }

    @Test
    void trustsOnlyTheExactHostAndDefaultPorts() {
        String text = "https://hi.thehyundai.com/a https://hi.thehyundai.com:80/b "
                + "http://HI.THEHYUNDAI.COM/c http://hi.thehyundai.com:80/i https://hi.thehyundai.com:443/d "
                + "https://user@hi.thehyundai.com/e https://hi.thehyundai.com:8080/f "
                + "https://evilhi.thehyundai.com/g https://sub.hi.thehyundai.com/h "
                + "https://[bad https://hi.thehyundai.com/valid ftp://hi.thehyundai.com/nope";

        SelectorsUrlEvidenceExtractor.Result result = SelectorsUrlEvidenceExtractor.extract(text);

        assertThat(result.trustedUrls()).containsExactly(
                "http://HI.THEHYUNDAI.COM/c",
                "http://hi.thehyundai.com:80/i",
                "https://hi.thehyundai.com/a",
                "https://hi.thehyundai.com/valid",
                "https://hi.thehyundai.com:443/d");
        assertThat(result.matchedUrls()).contains(
                "https://[bad",
                "https://user@hi.thehyundai.com/e",
                "https://hi.thehyundai.com:8080/f",
                "https://evilhi.thehyundai.com/g",
                "https://sub.hi.thehyundai.com/h",
                "https://hi.thehyundai.com/valid");
        assertThat(result.matchedUrls()).noneMatch(url -> url.startsWith("ftp://"));
    }
}
