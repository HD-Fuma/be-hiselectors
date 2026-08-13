package com.fuma.hiselectors.content.classifier;

import com.fuma.hiselectors.content.client.dto.RawContent;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 셀렉터스 콘텐츠 여부 판별 */
@Component
public class SelectorsContentClassifier {

    private static final String PRODUCT_GROUP_URL =
            "hi.thehyundai.com/sellectors/manage/shop/";
    private static final Pattern THE_HYUNDAI_KEYWORD =
            Pattern.compile("(?iu)[\\p{L}\\p{N}_]*더현대[\\p{L}\\p{N}_]*");
    private static final Pattern SELECTORS_KEYWORD =
            Pattern.compile("(?iu)셀렉터스(?![\\p{L}\\p{N}_])");

    public boolean isSelectorsContent(RawContent rawContent, String selectorsCode) {
        // 셀렉터스 콘텐츠 판별 대상 (본문)
        String caption = rawContent == null ? null : rawContent.caption();
        if (!StringUtils.hasText(caption)) {
            return false;
        }

        return hasReferralCode(caption, selectorsCode)
                || hasProductUrl(caption, selectorsCode)
                || hasProductGroupUrl(caption, selectorsCode)
                || hasDesignatedKeywords(caption);
    }

    // 셀렉터스 레퍼럴 코드 확인
    private boolean hasReferralCode(String caption, String selectorsCode) {
        return StringUtils.hasText(selectorsCode)
                && Pattern.compile(
                                "(?iu)(?<![A-Za-z0-9_])%s(?![A-Za-z0-9_])"
                                        .formatted(Pattern.quote(selectorsCode.trim())))
                        .matcher(caption)
                        .find();
    }

    // 셀렉터스 상품 URL 확인
    private boolean hasProductUrl(String caption, String selectorsCode) {
        return StringUtils.hasText(selectorsCode)
                && Pattern.compile(
                                "(?iu)https?://hi\\.thehyundai\\.com/product/\\S*"
                                        + "[?&]ptrsRefCd=%s(?![A-Za-z0-9_])"
                                                .formatted(Pattern.quote(
                                                        selectorsCode.trim())))
                        .matcher(caption)
                        .find();
    }

    // 셀렉터스 상품 그룹 URL 확인
    private boolean hasProductGroupUrl(String caption, String selectorsCode) {
        return StringUtils.hasText(selectorsCode)
                && caption.toLowerCase(Locale.ROOT).contains(
                        PRODUCT_GROUP_URL
                                + selectorsCode.trim().toLowerCase(Locale.ROOT) + "/");
    }

    // 지정 키워드 동시 포함 확인
    private boolean hasDesignatedKeywords(String caption) {
        return THE_HYUNDAI_KEYWORD.matcher(caption).find()
                && SELECTORS_KEYWORD.matcher(caption).find();
    }
}
