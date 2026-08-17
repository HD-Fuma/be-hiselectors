package com.fuma.hiselectors.content.classifier;

import com.fuma.hiselectors.content.client.dto.RawContent;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 셀렉터스 콘텐츠 여부 판별 */
@Component
public class SelectorsContentClassifier {

    public boolean isSelectorsContent(RawContent rawContent) {
        String caption = rawContent.caption();
        return PRODUCT_URL_PATTERN.matcher(caption).find()
                || SHOP_URL_PATTERN.matcher(caption).find()
                || hasDesignatedKeywords(caption);
    }

    // 상품 URL
    private static final Pattern PRODUCT_URL_PATTERN = Pattern.compile(
            "https?://hi\\.thehyundai\\.com/product/[^/?#\\s]+\\?"
                    + "(?:[^&\\s#]*&)*ptrsRefCd=RC[A-Z0-9]+"
                    + "(?=$|[&#\\s.,!;:)'\\]}\">])",
            Pattern.CASE_INSENSITIVE);

    // 셀렉터스 샵, 그룹 URL
    private static final Pattern SHOP_URL_PATTERN =
            Pattern.compile(
                    "https?://hi\\.thehyundai\\.com/sellectors/manage/shop/"
                            + "RC[A-Z0-9]+(?:/[^/?#\\s]+)?/?(?=$|[?#\\s.,!;:)'\\]}\">])",
                    Pattern.CASE_INSENSITIVE);

    // 키워드 목록
    private static final List<String> REQUIRED_KEYWORDS = List.of(
            "더현대",
            "셀렉터스"
    );

    // 키워드 동시 포함 확인
    private boolean hasDesignatedKeywords(String caption) {
        return REQUIRED_KEYWORDS.stream().allMatch(caption::contains);
    }
}
